"""File-level ingestion helpers.

The normal pipeline consumes text. This module is the boundary which turns an
uploaded file payload into text + a suggested ``source_type``. It intentionally
uses only the Python standard library so the same path can run inside Chaquopy
on Android.

Supported directly:
  - .txt, .md, .markdown, .csv, .json, .html/.htm
  - .docx and .pptx via Office Open XML zip/XML extraction
  - .pdf best-effort text extraction for selectable/simple PDFs; scanned PDFs
    return a clear diagnostic rather than pretending they were read.
"""
from __future__ import annotations

import base64
import csv
import html
import io
import json
import re
import unicodedata
import zlib
import zipfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

from ..core.errors import ValidationError

_TEXT_EXTS = {".txt", ".text", ".rtf"}
_MARKDOWN_EXTS = {".md", ".markdown"}
_SLIDE_EXTS = {".pptx"}
_DOCX_EXTS = {".docx"}
_HTML_EXTS = {".html", ".htm"}
_STRUCTURED_TEXT_EXTS = {".csv", ".tsv", ".json", ".srt", ".tex"}
_IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif", ".tiff", ".tif"}
_ZIP_EXTS = {".zip"}

# Phone-first limits: AntiROTAid must run on the user's device with no server.
# Large textbooks should be imported by chapter/lesson, not as one huge blob.
# Real lecture decks are routinely 20-60MB because of embedded media; we strip
# that media for PPTX before this check (see _slim_pptx_media), so the cap really
# only guards genuinely huge text payloads.
_MAX_UPLOAD_BYTES = 160_000_000
_MAX_EXTRACTED_CHARS = 180_000


# --- Real content validation (Phase 6) --------------------------------------
# The engine must NOT accept a file just because of its extension. Each importer
# proves the bytes are actually the claimed format and carry usable content, and
# raises a ValidationError carrying a STABLE code the Android UI localizes.

def _looks_like_pdf(data: bytes) -> bool:
    """True only if the bytes are a real PDF. A PDF must begin with ``%PDF-``
    (the spec allows a few junk bytes before it) and contain the ``%%EOF`` trailer
    somewhere. A renamed .txt/.docx never satisfies this."""
    head = data[:1024]
    if b"%PDF-" not in head:
        return False
    return b"%%EOF" in data[-2048:] or b"/Type" in data or b"obj" in data


def _count_real_pdf_pages(data: bytes) -> int:
    """Count genuine page objects (``/Type /Page``). Unlike the old helper this
    does NOT default to 1 — a fake/empty PDF must count 0 so it can be rejected."""
    raw = data.decode("latin-1", errors="ignore")
    return len(re.findall(r"/Type\s*/Page\b", raw))


# Minimal, dependency-free image decoders: confirm the magic bytes AND read
# plausible pixel dimensions. If dimensions cannot be read the bytes are not a
# decodable image of that family, so the import is rejected.
def _img_dims_png(d: bytes):
    if d[:8] != b"\x89PNG\r\n\x1a\n" or d[12:16] != b"IHDR":
        return None
    w = int.from_bytes(d[16:20], "big"); h = int.from_bytes(d[20:24], "big")
    return (w, h) if w > 0 and h > 0 else None


def _img_dims_gif(d: bytes):
    if d[:6] not in (b"GIF87a", b"GIF89a"):
        return None
    w = int.from_bytes(d[6:8], "little"); h = int.from_bytes(d[8:10], "little")
    return (w, h) if w > 0 and h > 0 else None


def _img_dims_bmp(d: bytes):
    if d[:2] != b"BM" or len(d) < 26:
        return None
    w = int.from_bytes(d[18:22], "little", signed=True)
    h = int.from_bytes(d[22:26], "little", signed=True)
    return (abs(w), abs(h)) if w != 0 and h != 0 else None


def _img_dims_webp(d: bytes):
    if d[:4] != b"RIFF" or d[8:12] != b"WEBP":
        return None
    # Accept any well-formed VP8/VP8L/VP8X chunk; dimensions vary by sub-format,
    # so a valid container header is sufficient proof of a real WebP.
    return (1, 1) if d[12:15] in (b"VP8", b"VP8") or d[12:16] in (b"VP8 ", b"VP8L", b"VP8X") else None


def _img_dims_tiff(d: bytes):
    if d[:4] not in (b"II*\x00", b"MM\x00*"):
        return None
    return (1, 1)


def _img_dims_jpeg(d: bytes):
    if d[:3] != b"\xff\xd8\xff":
        return None
    i = 2
    n = len(d)
    while i + 9 < n:
        if d[i] != 0xFF:
            i += 1
            continue
        marker = d[i + 1]
        # Start-Of-Frame markers carry the real dimensions.
        if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
            h = int.from_bytes(d[i + 5:i + 7], "big")
            w = int.from_bytes(d[i + 7:i + 9], "big")
            return (w, h) if w > 0 and h > 0 else None
        if marker in (0xD8, 0xD9) or 0xD0 <= marker <= 0xD7:
            i += 2
            continue
        seg = int.from_bytes(d[i + 2:i + 4], "big")
        if seg < 2:
            return None
        i += 2 + seg
    # A JPEG with the right SOI but no readable SOF is treated as valid if it also
    # carries the EOI trailer (some progressive encoders defer SOF); else invalid.
    return (1, 1) if d[-2:] == b"\xff\xd9" else None


_IMG_VALIDATORS = {
    ".png": _img_dims_png,
    ".jpg": _img_dims_jpeg, ".jpeg": _img_dims_jpeg,
    ".gif": _img_dims_gif,
    ".bmp": _img_dims_bmp,
    ".webp": _img_dims_webp,
    ".tif": _img_dims_tiff, ".tiff": _img_dims_tiff,
}


def _sniff_image_dims(data: bytes):
    """Return (w, h) for the first image family whose magic matches, else None.
    Extension-independent: we try every known decoder so a mislabeled-but-real
    image still validates, and a fake (e.g. text) file matches none."""
    for fn in (_img_dims_png, _img_dims_jpeg, _img_dims_gif,
               _img_dims_bmp, _img_dims_webp, _img_dims_tiff):
        dims = fn(data)
        if dims:
            return dims
    return None


def _validate_image_bytes(data: bytes) -> tuple[int, int]:
    """Raise ValidationError('invalid_image') unless the bytes decode as a real
    image of a supported family. Returns the (w, h) on success."""
    if not data:
        raise ValidationError(
            "empty image file", code="invalid_image",
            public_message="Imaginea este goală sau nu poate fi citită.",
            field_name="file",
        )
    dims = _sniff_image_dims(data)
    if dims is None:
        raise ValidationError(
            "file is not a decodable image", code="invalid_image",
            public_message="Fișierul nu este o imagine validă (PNG/JPG/GIF/BMP/WEBP).",
            field_name="file",
        )
    return dims


def _slim_pptx_media(data: bytes) -> bytes:
    """Drop embedded media/video from a PPTX so a media-bloated deck (often
    80-95MB) fits the offline memory budget. Slide TEXT lives in ppt/slides/*.xml
    and is untouched — only the heavy assets the text extractor never reads are
    removed. Returns the original bytes if anything goes wrong."""
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as zin:
            if not any(n.startswith("ppt/slides/slide") for n in zin.namelist()):
                return data
            buf = io.BytesIO()
            with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zout:
                for item in zin.infolist():
                    low = item.filename.lower()
                    if "/media/" in low or "/embeddings/" in low:
                        continue
                    zout.writestr(item, zin.read(item.filename))
            return buf.getvalue()
    except (zipfile.BadZipFile, OSError):
        return data


@dataclass
class FileExtractionResult:
    text: str
    source_type: str
    metadata: dict = field(default_factory=dict)

    def to_json(self) -> dict:
        return {
            "text_chars": len(self.text),
            "source_type": self.source_type,
            "metadata": self.metadata,
        }


def decode_payload(payload: str | bytes, *, encoding: str = "base64") -> bytes:
    """Decode the JSON-safe payload sent by UI/Android.

    ``encoding='base64'`` is the safe default for binary files. ``'text'`` is
    supported for direct pasted/uploaded plain text in tests and CLI tools.
    """
    if isinstance(payload, bytes):
        return payload
    if encoding == "base64":
        try:
            return base64.b64decode(payload, validate=True)
        except Exception as exc:
            raise ValidationError(
                "file payload is not valid base64",
                code="corrupt_file",
                public_message="Fișierul nu a putut fi citit corect. Încearcă să îl încarci din nou.",
                field_name="payload",
            ) from exc
    if encoding == "text":
        return payload.encode("utf-8")
    raise ValidationError(
        f"unsupported file payload encoding: {encoding}",
        code="unsupported_format",
        public_message="Formatul încărcării nu este acceptat încă.",
        field_name="encoding",
    )


def extract_file(filename: str, payload: str | bytes, *,
                 encoding: str = "base64", with_media: bool = True,
                 with_figures: bool = False) -> FileExtractionResult:
    data = decode_payload(payload, encoding=encoding)
    suffix = Path(filename or "uploaded.txt").suffix.lower()
    # Do NOT strip PPTX media before card generation. Visual/title-only slides
    # need original media or a native/rendered slide image path for image cards.
    # If the deck is too large, reject honestly instead of deleting images and
    # producing fake placeholder cards.
    if len(data) > _MAX_UPLOAD_BYTES:
        raise ValidationError(
            "uploaded file too large for offline phone runtime",
            code="file_too_large",
            public_message="Fișierul este prea mare pentru procesare locală sigură. Încarcă lecția pe bucăți mai mici.",
            field_name="file",
            context={"max_bytes": _MAX_UPLOAD_BYTES, "actual_bytes": len(data)},
        )
    if not data:
        raise ValidationError(
            "uploaded file is empty", code="empty_document",
            public_message="Fișierul este gol.", field_name="file",
        )
    metadata = {"filename": filename, "extension": suffix, "bytes": len(data), "offline_limit_bytes": _MAX_UPLOAD_BYTES}

    if suffix in _TEXT_EXTS:
        text = _decode_text(data)
        if not text.strip():
            raise ValidationError(
                "text file has no readable content", code="empty_txt",
                public_message="Fișierul text este gol sau conține doar spații.",
                field_name="file",
            )
        return FileExtractionResult(_enforce_extracted_text_limit(text), "plain_file", {**metadata, "extractor": "text"})
    if suffix in _MARKDOWN_EXTS:
        text = _decode_text(data)
        return FileExtractionResult(_enforce_extracted_text_limit(text), "markdown", {**metadata, "extractor": "markdown"})
    if suffix in _HTML_EXTS:
        text = _html_to_text(_decode_text(data))
        return FileExtractionResult(_enforce_extracted_text_limit(text), "plain_file", {**metadata, "extractor": "html"})
    if suffix in _STRUCTURED_TEXT_EXTS:
        text = _structured_to_text(data, suffix)
        return FileExtractionResult(_enforce_extracted_text_limit(text), "plain_file", {**metadata, "extractor": suffix.lstrip(".")})
    if suffix in _DOCX_EXTS:
        text, details = _extract_docx(data)
        return FileExtractionResult(_enforce_extracted_text_limit(text), "plain_file", {**metadata, **details})
    if suffix in _SLIDE_EXTS:
        text, details = _extract_pptx(data, with_media=with_media, with_figures=with_figures)
        return FileExtractionResult(_enforce_extracted_text_limit(text), "slides", {**metadata, **details})
    if suffix == ".pdf":
        text, details = _extract_pdf_resilient(data, with_figures=with_figures)
        return FileExtractionResult(_enforce_extracted_text_limit(text), "pdf", {**metadata, **details})
    if suffix in _IMAGE_EXTS:
        # Images are valid AntiROTAid source units even without OCR: the correct
        # card is an image card associated with the uploaded picture, not a fake
        # generated text card. OCR can be added later as an optional enrichment.
        # But first PROVE the bytes are a decodable image — a renamed .txt must be
        # rejected, not turned into a blank "image" card.
        w, h = _validate_image_bytes(data)
        text = "Image 1:\n"
        return FileExtractionResult(text, "image", {**metadata, "extractor": "image_passthrough",
                                                    "image_card_required": True,
                                                    "image_width": w, "image_height": h})
    if suffix in _ZIP_EXTS:
        text, details = _extract_zip_images(data, with_media=with_media)
        return FileExtractionResult(_enforce_extracted_text_limit(text), "image", {**metadata, **details})

    # Last resort: try to decode as text. This lets uncommon text formats still
    # become cards instead of being rejected just because the extension is new.
    text = _decode_text(data)
    if not text.strip():
        raise ValidationError(
            "unsupported uploaded file",
            code="unsupported_format",
            public_message="Nu am găsit text utilizabil în fișier. Încarcă o variantă text, DOCX, PPTX sau PDF cu text selectabil.",
            field_name="filename",
        )
    return FileExtractionResult(_enforce_extracted_text_limit(text), "plain_file", {**metadata, "extractor": "fallback_text"})


def _enforce_extracted_text_limit(text: str, *, field_name: str = "file") -> str:
    if len(text or "") <= _MAX_EXTRACTED_CHARS:
        return text
    raise ValidationError(
        "extracted material too large for offline phone runtime",
        code="file_too_large",
        public_message="Materialul extras este prea lung pentru un deck bun pe telefon. Importă pe capitole/lecții mai mici.",
        field_name=field_name,
        context={"max_chars": _MAX_EXTRACTED_CHARS, "actual_chars": len(text or "")},
    )


def _decode_text(data: bytes) -> str:
    for enc in ("utf-8-sig", "utf-8", "utf-16", "cp1250", "latin-1"):
        try:
            return data.decode(enc)
        except UnicodeDecodeError:
            continue
    return data.decode("utf-8", errors="replace")


def _html_to_text(raw: str) -> str:
    raw = re.sub(r"(?is)<(script|style).*?>.*?</\1>", " ", raw)
    raw = re.sub(r"(?i)<br\s*/?>", "\n", raw)
    raw = re.sub(r"(?i)</(p|div|li|h[1-6]|tr)>", "\n", raw)
    raw = re.sub(r"<[^>]+>", " ", raw)
    return html.unescape(raw)


def _structured_to_text(data: bytes, suffix: str) -> str:
    text = _decode_text(data)
    if suffix in {".csv", ".tsv"}:
        dialect = "excel-tab" if suffix == ".tsv" else "excel"
        rows: list[str] = []
        try:
            for row in csv.reader(io.StringIO(text), dialect=dialect):
                rows.append(" | ".join(cell.strip() for cell in row if cell.strip()))
        except csv.Error:
            return text
        return "\n".join(r for r in rows if r)
    if suffix == ".json":
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError:
            return text
        return _flatten_json(parsed)
    return text


def _flatten_json(obj, *, prefix: str = "") -> str:
    lines: list[str] = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            key = f"{prefix}.{k}" if prefix else str(k)
            lines.append(_flatten_json(v, prefix=key))
    elif isinstance(obj, list):
        for idx, v in enumerate(obj):
            lines.append(_flatten_json(v, prefix=f"{prefix}[{idx}]"))
    else:
        lines.append(f"{prefix}: {obj}" if prefix else str(obj))
    return "\n".join(x for x in lines if x)


def _xml_texts(xml_bytes: bytes) -> list[str]:
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return []
    texts: list[str] = []
    # OOXML text nodes are usually w:t or a:t. Namespace-insensitive check.
    for node in root.iter():
        if node.text and node.tag.rsplit("}", 1)[-1] in {"t", "instrText"}:
            value = node.text.strip()
            if value:
                texts.append(value)
    return texts


def _docx_paragraph_text(p_el) -> str:
    """Text of one Word w:p paragraph; w:tab -> space, w:br -> space. Office Math
    (m:oMath) equations are rebuilt structurally instead of flattened."""
    return _walk_paragraph(p_el, ("t",))


def _extract_zip_images(data: bytes, with_media: bool = True) -> tuple[str, dict]:
    """A ZIP of images -> one image card per valid picture (partial success). Each
    image becomes an "Image N:" unit; the picture is attached as base64 keyed
    "image_N". Non-image / corrupt / oversize entries are skipped (never crash).
    Reports how many were used vs skipped for the import report."""
    try:
        zf = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile as exc:
        raise ValidationError(
            "invalid ZIP file",
            code="invalid_zip",
            public_message="Arhiva ZIP nu poate fi citită. Încarc-o din nou.",
            field_name="file",
        ) from exc
    entries = sorted(
        n for n in zf.namelist()
        if not n.endswith("/") and Path(n).suffix.lower() in _IMG_CONTENT_TYPE
        and not n.startswith("__MACOSX/")
    )
    media: dict[str, dict] = {}
    used = 0
    skipped = 0
    total = 0
    idx = 0
    for name in entries:
        try:
            blob = zf.read(name)
        except (KeyError, OSError, zipfile.BadZipFile):
            skipped += 1
            continue
        if not blob or len(blob) > _MAX_SLIDE_IMG_BYTES:
            skipped += 1
            continue
        if total + len(blob) > _MAX_TOTAL_SLIDE_IMG_BYTES:
            skipped += 1
            continue
        idx += 1
        used += 1
        total += len(blob)
        if with_media:
            media[f"image_{idx}"] = {
                "base64": base64.b64encode(blob).decode("ascii"),
                "content_type": _IMG_CONTENT_TYPE[Path(name).suffix.lower()],
            }
    # Count non-image entries as skipped too, for an honest report.
    skipped += sum(1 for n in zf.namelist()
                   if not n.endswith("/") and not n.startswith("__MACOSX/")
                   and Path(n).suffix.lower() not in _IMG_CONTENT_TYPE)
    if used == 0:
        raise ValidationError(
            "ZIP has no usable images",
            code="no_readable_content",
            public_message="Arhiva nu conține imagini valide (JPG/PNG/WEBP).",
            field_name="file",
        )
    text = "\n\n".join(f"Image {i}:\n" for i in range(1, used + 1))
    details = {"extractor": "zip_images", "images_used": used, "images_skipped": skipped,
               "image_card_required": True}
    if media:
        details["media"] = media
    return text, details


def _extract_docx(data: bytes) -> tuple[str, dict]:
    """DOCX PARAGRAPHS: each Word paragraph becomes its own block (so the cardifier
    makes one card per paragraph). Tables are flattened row-by-row into blocks.
    Order is preserved by walking the document body. Robust: a malformed part is
    skipped, never crashes."""
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as zf:
            names = set(zf.namelist())
            # A real DOCX is an OOXML package: it MUST carry the content-types
            # map and the main document part. A random zip renamed .docx fails here.
            if "[Content_Types].xml" not in names or "word/document.xml" not in names:
                raise ValidationError(
                    "not a Word (DOCX) package",
                    code="invalid_docx",
                    public_message="Fișierul nu este un document Word (DOCX) valid.",
                    field_name="file",
                )
            root = ET.fromstring(zf.read("word/document.xml"))
    except zipfile.BadZipFile as exc:
        raise ValidationError(
            "invalid DOCX file",
            code="invalid_docx",
            public_message="DOCX-ul nu poate fi citit. Exportă-l din nou sau încarcă textul direct.",
            field_name="file",
        ) from exc
    except ET.ParseError as exc:
        raise ValidationError(
            "DOCX document.xml is not parseable",
            code="corrupt_file",
            public_message="DOCX-ul pare corupt. Exportă-l din nou.",
            field_name="file",
        ) from exc

    body = next((el for el in root.iter() if _local(el.tag) == "body"), root)
    blocks: list[str] = []
    paras = 0
    tables = 0
    for child in body:
        tag = _local(child.tag)
        if tag == "p":
            t = _docx_paragraph_text(child)
            if t:
                blocks.append(t)
                paras += 1
        elif tag == "tbl":
            # Flatten the table into "cell | cell" rows so it stays one readable block.
            rows: list[str] = []
            for tr in child.iter():
                if _local(tr.tag) != "tr":
                    continue
                cells = [
                    " ".join(_docx_paragraph_text(p) for p in tc.iter() if _local(p.tag) == "p").strip()
                    for tc in tr if _local(tc.tag) == "tc"
                ]
                cells = [c for c in cells if c]
                if cells:
                    rows.append(" | ".join(cells))
            if rows:
                blocks.append("\n".join(rows))
                tables += 1

    text = "\n\n".join(blocks)
    if not text.strip():
        raise ValidationError(
            "DOCX has no readable text",
            code="no_readable_content",
            public_message="DOCX-ul nu conține text citibil pentru carduri.",
            field_name="file",
        )
    return text, {"extractor": "docx_paragraphs", "paragraphs": paras, "tables": tables}


def _pptx_slide_number(name: str) -> int:
    m = re.search(r"/slide(\d+)\.xml$", name)
    return int(m.group(1)) if m else 10**9


def _local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


# --- OMML (Office Math) structural rendering ---------------------------------
# PowerPoint/Word store equations as Office MathML (``m:oMath``). Iterating text
# nodes flat (``m:t``) loses the 2-D layout: a fraction collapses to "ab", an
# exponent to "x2". We instead walk the math tree and rebuild readable Unicode
# (fractions a/b, x², xᵢ, √, ∑/∫ with limits) — deterministic, offline, no list.

def _omml_child(el, local: str):
    for c in el:
        if _local(c.tag) == local:
            return c
    return None


def _omml_prop(el, prop_local: str):
    """Value of ``<xxxPr><prop_local m:val="V"/></xxxPr>`` (e.g. n-ary char)."""
    for pr in el:
        if not _local(pr.tag).endswith("Pr"):
            continue
        for c in pr:
            if _local(c.tag) == prop_local:
                for k, v in c.attrib.items():
                    if k.rsplit("}", 1)[-1] == "val":
                        return v
    return None


def _omml_join(el) -> str:
    return "" if el is None else "".join(_omml_text(c) for c in el)


def _omml_text(el) -> str:
    """Render one OMML element subtree to readable Unicode math text."""
    from ..conceptgen.mathtext import to_superscript, to_subscript
    ln = _local(el.tag)
    if ln == "t":
        return el.text or ""
    if ln == "br":
        return " "
    if ln.endswith("Pr"):          # *Pr = properties, never content
        return ""
    if ln == "f":                  # fraction
        return f"({_omml_join(_omml_child(el, 'num'))})/({_omml_join(_omml_child(el, 'den'))})"
    if ln == "rad":                # radical
        deg = _omml_join(_omml_child(el, "deg"))
        body = _omml_join(_omml_child(el, "e"))
        return (to_superscript(deg) if deg.strip() else "") + f"√({body})"
    if ln == "sSup":
        return _omml_join(_omml_child(el, "e")) + to_superscript(_omml_join(_omml_child(el, "sup")))
    if ln == "sSub":
        return _omml_join(_omml_child(el, "e")) + to_subscript(_omml_join(_omml_child(el, "sub")))
    if ln == "sSubSup":
        return (_omml_join(_omml_child(el, "e"))
                + to_subscript(_omml_join(_omml_child(el, "sub")))
                + to_superscript(_omml_join(_omml_child(el, "sup"))))
    if ln == "sPre":               # pre-scripts
        return (to_subscript(_omml_join(_omml_child(el, "sub")))
                + to_superscript(_omml_join(_omml_child(el, "sup")))
                + _omml_join(_omml_child(el, "e")))
    if ln == "nary":               # ∑ / ∫ / ∏ with limits
        sym = _omml_prop(el, "chr") or "∫"
        sub = _omml_join(_omml_child(el, "sub"))
        sup = _omml_join(_omml_child(el, "sup"))
        body = _omml_join(_omml_child(el, "e"))
        return (sym + (to_subscript(sub) if sub.strip() else "")
                + (to_superscript(sup) if sup.strip() else "")
                + (f" {body}" if body.strip() else ""))
    if ln == "d":                  # delimiter ( ) [ ] { }
        beg = _omml_prop(el, "begChr")
        end = _omml_prop(el, "endChr")
        beg = "(" if beg is None else beg
        end = ")" if end is None else end
        inner = "".join(_omml_text(c) for c in el if _local(c.tag) == "e")
        return beg + inner + end
    if ln == "func":               # named function: sin, lim …
        return _omml_join(_omml_child(el, "fName")) + "(" + _omml_join(_omml_child(el, "e")) + ")"
    if ln == "m":                  # matrix
        rows = ["  ".join(_omml_join(cell) for cell in r if _local(cell.tag) == "e")
                for r in el if _local(r.tag) == "mr"]
        return "[ " + " ; ".join(rows) + " ]"
    return "".join(_omml_text(c) for c in el)   # container: e/num/den/oMath/…


def _walk_paragraph(p_el, text_locals: tuple) -> str:
    """Collect a paragraph's text, rendering any OMML math subtree as a unit
    (so its inner m:t runs are not also flattened). ``text_locals`` are the
    ordinary run tags for the format (a:t for PPTX, w:t for DOCX)."""
    out: list[str] = []

    def walk(el):
        ln = _local(el.tag)
        if ln in ("oMath", "oMathPara"):
            out.append(" " + _omml_text(el) + " ")
            return                          # rendered whole; don't descend
        if ln in text_locals and el.text:
            out.append(el.text)
        elif ln in ("br", "tab"):
            out.append(" ")
        for c in el:
            walk(c)

    for c in p_el:
        walk(c)
    return re.sub(r"\s+", " ", "".join(out)).strip()


def _pptx_paragraph_text(p_el) -> str:
    """Text of one a:p paragraph; a soft line break (a:br) becomes a space so
    runs split across lines don't glue together ("ExamplesFrom"). Office Math
    (m:oMath) is rebuilt structurally instead of flattened."""
    return _walk_paragraph(p_el, ("t", "instrText"))


def _pptx_paragraph_level(p_el) -> int:
    """Indent/outline level of one a:p paragraph (a:pPr@lvl, default 0). This is
    the bullet nesting depth PowerPoint stores — a top-level concept is level 0,
    its sub-points level 1, etc. We keep it so the cardifier can split a dense
    slide into one card per top-level concept ("atomic" granularity) instead of
    flattening every bullet into one indistinct wall."""
    for el in p_el:
        if _local(el.tag) == "pPr":
            try:
                return max(0, int(el.get("lvl") or "0"))
            except (TypeError, ValueError):
                return 0
    return 0


def _pptx_slide_struct(xml_bytes: bytes) -> tuple[str | None, list[dict]]:
    """Return (title, structure) for one slide using the title placeholder
    (ph type=title/ctrTitle), so the concept name is reliable instead of guessed
    from document order.

    ``structure`` is the ordered list of body paragraphs, each ``{"lvl", "text"}``
    where ``lvl`` is the PowerPoint outline depth (0 = top-level concept). Keeping
    the depth lets the cardifier group sub-points under their parent concept for
    "atomic" granularity. The flat bullet list is recoverable as
    ``[s["text"] for s in structure]``."""
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return None, []
    title: str | None = None
    structure: list[dict] = []
    box = 0  # body text-box index — each separate box is usually one concept group
    for sp in root.iter():
        if _local(sp.tag) != "sp":
            continue
        ph_type = None
        for el in sp.iter():
            if _local(el.tag) == "ph":
                ph_type = el.get("type")
                break
        paras: list[dict] = []
        for tx in sp.iter():
            if _local(tx.tag) == "txBody":
                for p in tx.iter():
                    if _local(p.tag) == "p":
                        t = _pptx_paragraph_text(p)
                        # Skip empty bullets PowerPoint exports as a literal
                        # placeholder token like "<None>" — they are not content.
                        if t and not re.fullmatch(r"<[^>]{0,24}>", t):
                            paras.append({"lvl": _pptx_paragraph_level(p), "text": t})
                break
        if not paras:
            continue
        if ph_type in ("title", "ctrTitle") and title is None:
            title = " ".join(pp["text"] for pp in paras)
        else:
            box += 1
            for pp in paras:
                pp["box"] = box
            structure.extend(paras)
    return title, structure


# Per-image and total budgets for embedding slide pictures as base64. Diagram
# PNGs in lecture decks are usually 30-400KB; we skip oversize photos and stop
# once the total would strain the JNI bridge / phone memory.
_MAX_SLIDE_IMG_BYTES = 900_000
_MAX_TOTAL_SLIDE_IMG_BYTES = 14_000_000
_IMG_CONTENT_TYPE = {
    ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
    ".gif": "image/gif", ".bmp": "image/bmp", ".webp": "image/webp",
    ".tif": "image/tiff", ".tiff": "image/tiff",
}


def _pptx_slide_images(zf: "zipfile.ZipFile") -> dict[int, dict]:
    """Map slide number -> {"base64", "content_type"} for the largest embedded
    picture on that slide. Resolves slideN.xml.rels image relationships against
    ppt/media/*. Returns only slides whose picture fits the offline budget."""
    names = set(zf.namelist())
    out: dict[int, dict] = {}
    total = 0
    slide_names = sorted(
        (n for n in names if n.startswith("ppt/slides/slide") and n.endswith(".xml")),
        key=_pptx_slide_number,
    )
    for name in slide_names:
        slide_no = _pptx_slide_number(name)
        rels = f"ppt/slides/_rels/{name.rsplit('/', 1)[-1]}.rels"
        if rels not in names:
            continue
        try:
            root = ET.fromstring(zf.read(rels))
        except ET.ParseError:
            continue
        targets: list[str] = []
        for rel in root.iter():
            if _local(rel.tag) != "Relationship":
                continue
            rtype = (rel.get("Type") or "").lower()
            target = rel.get("Target") or ""
            if "image" in rtype and target:
                # Normalize "../media/imageN.png" relative to ppt/slides/.
                norm = target.replace("\\", "/")
                if norm.startswith("../"):
                    norm = "ppt/" + norm[3:]
                elif norm.startswith("/"):
                    norm = norm.lstrip("/")
                else:
                    norm = "ppt/slides/" + norm
                targets.append(norm)
        best: tuple[int, bytes, str] | None = None  # (size, bytes, ext)
        for t in targets:
            if t not in names:
                continue
            ext = Path(t).suffix.lower()
            if ext not in _IMG_CONTENT_TYPE:
                continue
            try:
                blob = zf.read(t)
            except (KeyError, OSError):
                continue
            if not blob or len(blob) > _MAX_SLIDE_IMG_BYTES:
                continue
            if best is None or len(blob) > best[0]:
                best = (len(blob), blob, ext)
        if best is None:
            continue
        if total + best[0] > _MAX_TOTAL_SLIDE_IMG_BYTES:
            break
        total += best[0]
        out[slide_no] = {
            "base64": base64.b64encode(best[1]).decode("ascii"),
            "content_type": _IMG_CONTENT_TYPE[best[2]],
        }
    return out


def _pptx_rid_targets(zf: "zipfile.ZipFile", slide_name: str, names: set) -> dict[str, str]:
    """Map relationship id (rId..) -> normalized media path for one slide's rels.
    Used to resolve each <p:pic>'s <a:blip r:embed> to its actual picture file."""
    rels = f"ppt/slides/_rels/{slide_name.rsplit('/', 1)[-1]}.rels"
    if rels not in names:
        return {}
    try:
        root = ET.fromstring(zf.read(rels))
    except ET.ParseError:
        return {}
    out: dict[str, str] = {}
    for rel in root.iter():
        if _local(rel.tag) != "Relationship":
            continue
        rid = rel.get("Id") or ""
        target = rel.get("Target") or ""
        if not (rid and target):
            continue
        norm = target.replace("\\", "/")
        if norm.startswith("../"):
            norm = "ppt/" + norm[3:]
        elif norm.startswith("/"):
            norm = norm.lstrip("/")
        else:
            norm = "ppt/slides/" + norm
        out[rid] = norm
    return out


# Per-slide figure selection for Captură mode. A content slide that PowerPoint
# stores as many small pictures (a multi-panel figure cut into tiles, icon strips,
# logo rows) must not explode into 6-7 near-identical cards.
_MAX_FIGS_PER_SLIDE = 4
_FIG_FRAGMENT_RATIO = 0.20   # drop figures smaller than this fraction of the slide's biggest


def _pptx_slide_figures(zf: "zipfile.ZipFile", encode: bool = True) -> dict[int, list[dict]]:
    """Map slide number -> list of the SIGNIFICANT embedded figures on that slide,
    in reading order (top-to-bottom, left-to-right), each ``{"base64",
    "content_type", "x", "y"}``. Powers "Captură" mode (one card per figure).

    Each embedded picture is already a discrete cropped asset, so no image
    processing is needed. We do filter so a slide does not over-produce cards:
    tiny fragments (< 20% of the slide's largest picture — usually icons or tiles
    of one composite) are dropped, exact-duplicate pictures are de-duplicated, and
    at most a few figures per slide are kept."""
    import hashlib
    names = set(zf.namelist())
    out: dict[int, list[dict]] = {}
    total = 0
    seen_hashes: set[str] = set()
    slide_names = sorted(
        (n for n in names if n.startswith("ppt/slides/slide") and n.endswith(".xml")),
        key=_pptx_slide_number,
    )
    for name in slide_names:
        slide_no = _pptx_slide_number(name)
        rid_target = _pptx_rid_targets(zf, name, names)
        if not rid_target:
            continue
        try:
            root = ET.fromstring(zf.read(name))
        except ET.ParseError:
            continue
        # 1) Gather candidate pictures (raw bytes + position), not yet encoded.
        cands: list[dict] = []
        for pic in root.iter():
            if _local(pic.tag) != "pic":
                continue
            embed = None
            x = y = 0
            for el in pic.iter():
                ln = _local(el.tag)
                if ln == "blip":
                    embed = (el.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}embed")
                             or el.get("embed"))
                elif ln == "off":
                    try:
                        x, y = int(el.get("x") or 0), int(el.get("y") or 0)
                    except (TypeError, ValueError):
                        x = y = 0
            target = rid_target.get(embed or "")
            if not target or target not in names:
                continue
            ext = Path(target).suffix.lower()
            if ext not in _IMG_CONTENT_TYPE:
                continue
            try:
                blob = zf.read(target)
            except (KeyError, OSError):
                continue
            if not blob or len(blob) > _MAX_SLIDE_IMG_BYTES:
                continue
            cands.append({"blob": blob, "ext": ext, "x": x, "y": y})
        if not cands:
            continue
        # 2) Drop tiny fragments relative to the slide's largest picture, and cap
        #    the count (keep the biggest), then restore reading order.
        biggest = max(len(c["blob"]) for c in cands)
        cands = [c for c in cands if len(c["blob"]) >= biggest * _FIG_FRAGMENT_RATIO]
        if len(cands) > _MAX_FIGS_PER_SLIDE:
            cands = sorted(cands, key=lambda c: len(c["blob"]), reverse=True)[:_MAX_FIGS_PER_SLIDE]
        cands.sort(key=lambda c: (c["y"], c["x"]))
        # 3) De-duplicate identical pictures (repeated logos), budget-gate, encode.
        #    When encode=False (count-only load) we keep the figure entry but skip
        #    the base64 — so the card COUNT matches the real feed without paying to
        #    transfer ~14MB over JNI just to show a number.
        figures: list[dict] = []
        for c in cands:
            h = hashlib.sha1(c["blob"]).hexdigest()
            if h in seen_hashes:
                continue
            if encode and total + len(c["blob"]) > _MAX_TOTAL_SLIDE_IMG_BYTES:
                break
            seen_hashes.add(h)
            total += len(c["blob"])
            figures.append({
                "base64": base64.b64encode(c["blob"]).decode("ascii") if encode else "",
                "content_type": _IMG_CONTENT_TYPE[c["ext"]],
                "x": c["x"], "y": c["y"],
            })
        if figures:
            out[slide_no] = figures
        if encode and total >= _MAX_TOTAL_SLIDE_IMG_BYTES:
            break
    return out


def _extract_pptx(data: bytes, with_media: bool = True, with_figures: bool = False) -> tuple[str, dict]:
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as zf:
            names = set(zf.namelist())
            # A real PPTX is an OOXML package with the content-types map and a
            # presentation part. A random zip renamed .pptx fails this check.
            if "[Content_Types].xml" not in names or "ppt/presentation.xml" not in names:
                raise ValidationError(
                    "not a PowerPoint (PPTX) package",
                    code="invalid_pptx",
                    public_message="Fișierul nu este o prezentare PowerPoint (PPTX) validă.",
                    field_name="file",
                )
            slide_names = sorted(
                (n for n in zf.namelist()
                 if n.startswith("ppt/slides/slide") and n.endswith(".xml")),
                key=_pptx_slide_number,
            )
            slides: list[tuple[int, str]] = []
            structure_by_slide: dict[int, list[dict]] = {}
            for name in slide_names:
                slide_no = _pptx_slide_number(name)
                title, structure = _pptx_slide_struct(zf.read(name))
                bullets = [s["text"] for s in structure]
                lines: list[str] = []
                if title:
                    lines.append(title)          # title first => reliable concept name
                lines.extend(bullets)
                if not lines:                    # fallback: any text in document order
                    lines = _xml_texts(zf.read(name))
                body = "\n".join(lines).strip()
                # Important: preserve EVERY slide as a source unit. A slide with
                # no extractable text is not junk; it may be a diagram/photo-only
                # slide. The lesson cardifier will turn empty/title-only source
                # units into slide_image cards instead of dropping them.
                slides.append((slide_no, body))
                # Keep the outline (title + depth-tagged body paragraphs) so the
                # cardifier can split this slide into one card per top-level
                # concept when the owner picks "atomic" granularity.
                slide_struct = []
                if title:
                    slide_struct.append({"lvl": -1, "box": 0, "text": title, "is_title": True})
                slide_struct.extend({"lvl": s["lvl"], "box": s.get("box", 0), "text": s["text"], "is_title": False} for s in structure)
                if slide_struct:
                    structure_by_slide[slide_no] = slide_struct
            # Embedded slide pictures, keyed by slide number, so visual slides can
            # render the real diagram instead of a placeholder. Skipped for the
            # lightweight detail/count load — decoding every slide image into
            # base64 can allocate tens of MB and OOM on large decks.
            slide_images = _pptx_slide_images(zf) if with_media else {}
            # ALL embedded figures are heavy (one base64 blob each) and only the
            # Captură mode needs them — gate behind with_figures so the default
            # extract/as_is loads keep the old, lean media footprint (no OOM).
            # In capture mode we still enumerate figures for the count-only load
            # (encode=with_media) so "Cards: N" matches the real feed cheaply.
            slide_figures = _pptx_slide_figures(zf, encode=with_media) if with_figures else {}
    except zipfile.BadZipFile as exc:
        raise ValidationError(
            "invalid PPTX file",
            code="invalid_pptx",
            public_message="PPTX-ul nu poate fi citit. Exportă-l din nou sau încarcă textul slide-urilor.",
            field_name="file",
        ) from exc
    text = "\n\n".join(f"Slide {num}:\n{body}" for num, body in slides)
    if not text.strip():
        raise ValidationError(
            "PPTX has no readable text",
            code="no_readable_content",
            public_message="PPTX-ul nu conține text citibil pentru carduri.",
            field_name="file",
        )
    details = {"extractor": "pptx_ooxml_struct", "slides_used": len([1 for _, body in slides if body.strip()]),
               "slide_count": len(slides),
               "empty_or_visual_slides_preserved": len([1 for _, body in slides if not body.strip()]),
               "slide_order": "numeric"}
    if slide_images:
        # Keyed by "slide" + number so the bridge can attach by unit number.
        details["media"] = {f"slide_{num}": img for num, img in slide_images.items()}
    if slide_figures:
        # ALL figures per slide (reading order) for Captură mode — one card/figure.
        details["figures"] = {f"slide_{num}": figs for num, figs in slide_figures.items()}
    if structure_by_slide:
        # Per-slide outline (title + depth-tagged paragraphs) for atomic granularity.
        details["slide_structure"] = {str(num): st for num, st in structure_by_slide.items()}
    return text, details


def _safe_chr(n: int) -> str:
    """Codepoint -> char, dropping surrogate/out-of-range values so a stray CMap
    entry can never crash extraction."""
    if 0xD800 <= n <= 0xDFFF or n > 0x10FFFF:
        return ""
    return chr(n)


def _inflate(raw: bytes) -> bytes | None:
    """zlib-inflate a FlateDecode stream body. Tries the zlib header and the raw
    (headerless) deflate variant; returns None if neither decompresses."""
    for wbits in (zlib.MAX_WBITS, -zlib.MAX_WBITS):
        try:
            return zlib.decompressobj(wbits).decompress(raw)
        except zlib.error:
            continue
    return None


def _pdf_streams(data: bytes) -> list[bytes]:
    """Yield each stream object's bytes, FlateDecode-inflated when possible.

    Modern PDFs (incl. anything exported from Word/Google Docs/LibreOffice) store
    page content and the ToUnicode CMaps inside FlateDecode-compressed streams.
    The old extractor scraped the raw file, so it only ever saw the compressed
    binary — which is why it surfaced ``endstream``/mojibake as "concepts". We
    inflate here so the real content operators become visible.
    """
    streams: list[bytes] = []
    for m in re.finditer(rb"stream\r?\n", data):
        start = m.end()
        end = data.find(b"endstream", start)
        if end == -1:
            continue
        raw = data[start:end]
        if raw.endswith(b"\r\n"):
            raw = raw[:-2]
        elif raw[-1:] in (b"\n", b"\r"):
            raw = raw[:-1]
        inflated = _inflate(raw)
        streams.append(inflated if inflated is not None else raw)
    return streams


def _pdf_tounicode_map(streams: Iterable[bytes]) -> dict[int, str]:
    """Build a glyph-code -> Unicode map by merging every ToUnicode CMap found.

    Font subsets encode text as glyph ids (e.g. ``<002c0031..>`` = "IN.."), not
    Unicode, so the bytes are meaningless without the font's ToUnicode CMap. We
    merge all CMaps in the document into one map; for a single-typeface document
    that recovers the text (with diacritics) reliably.
    """
    cmap: dict[int, str] = {}
    for st in streams:
        if b"beginbfchar" not in st and b"beginbfrange" not in st:
            continue
        text = st.decode("latin-1", errors="ignore")
        for block in re.findall(r"beginbfchar(.*?)endbfchar", text, re.DOTALL):
            for src, dst in re.findall(r"<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>", block):
                cmap[int(src, 16)] = "".join(
                    _safe_chr(int(dst[i:i + 4], 16)) for i in range(0, len(dst) - 3, 4)
                )
        for block in re.findall(r"beginbfrange(.*?)endbfrange", text, re.DOTALL):
            for lo, hi, dst in re.findall(
                r"<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>", block
            ):
                base = int(dst, 16)
                for offset, code in enumerate(range(int(lo, 16), int(hi, 16) + 1)):
                    cmap[code] = _safe_chr(base + offset)
    return cmap


def _pdf_text_via_cmap(streams: list[bytes], cmap: dict[int, str]) -> str:
    """Decode Tj/TJ text-show operators in content streams through the CMap.

    A large kerning adjustment inside a TJ array marks a word break, so we turn
    it into a space; otherwise glued tokens like "valoaregreutate" appear."""
    def decode_hex(h: str) -> str:
        return "".join(cmap.get(int(h[i:i + 4], 16), "") for i in range(0, len(h) - 3, 4))

    pieces: list[str] = []
    for st in streams:
        if b"TJ" not in st and b"Tj" not in st:
            continue
        content = st.decode("latin-1", errors="ignore")
        for arr in re.finditer(r"\[(.*?)\]\s*TJ", content, re.DOTALL):
            buf: list[str] = []
            for tok in re.finditer(r"<([0-9A-Fa-f]+)>|(-?\d+\.?\d*)", arr.group(1)):
                if tok.group(1):
                    buf.append(decode_hex(tok.group(1)))
                elif tok.group(2) and abs(float(tok.group(2))) > 100:
                    buf.append(" ")
            line = "".join(buf).strip()
            if line:
                pieces.append(line)
        for one in re.finditer(r"<([0-9A-Fa-f]+)>\s*Tj", content):
            line = decode_hex(one.group(1)).strip()
            if line:
                pieces.append(line)
    return "\n".join(pieces)


def _extract_pdf_best_effort(data: bytes) -> tuple[str, dict]:
    # Real PDF layout extraction is a host concern. We first try the structured
    # path: inflate FlateDecode streams and decode the text-show operators through
    # the ToUnicode CMap (handles font-subset glyph encoding + diacritics). If a
    # PDF is old/simple enough that this finds nothing, we fall back to the raw
    # literal/hex scrape. Scanned/image-only PDFs are still rejected honestly.
    streams = _pdf_streams(data)
    cmap = _pdf_tounicode_map(streams)
    structured = ""
    if cmap:
        structured = _pdf_text_via_cmap(streams, cmap)
    structured = re.sub(r"[ \t]+", " ", structured).strip()
    if len(structured) >= 40:
        return structured, {
            "extractor": "pdf_content_stream",
            "note": "FlateDecode + ToUnicode CMap text",
            "cmap_entries": len(cmap),
        }

    raw = data.decode("latin-1", errors="ignore")
    literal = [m.group(1) for m in re.finditer(r"\(([^()]{3,})\)", raw)]
    hex_texts: list[str] = []
    for m in re.finditer(r"<([0-9A-Fa-f]{8,})>", raw):
        h = m.group(1)
        try:
            b = bytes.fromhex(h)
            if b.startswith(b"\xfe\xff"):
                val = b[2:].decode("utf-16-be", errors="ignore")
            else:
                val = b.decode("latin-1", errors="ignore")
        except ValueError:
            continue
        if any(ch.isalpha() for ch in val):
            hex_texts.append(val)
    text = "\n".join(_unescape_pdf_string(x) for x in [*literal, *hex_texts])
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) < 40:
        raise ValidationError(
            "no extractable text from PDF",
            code="no_readable_content",
            public_message="PDF-ul pare scanat sau fără text selectabil. Încarcă o variantă OCR/text ca să poată genera carduri.",
            field_name="file",
        )
    return text, {"extractor": "pdf_best_effort", "note": "selectable/simple PDF text only"}


def _unescape_pdf_string(value: str) -> str:
    value = value.replace(r"\(", "(").replace(r"\)", ")").replace(r"\\", "\\")
    value = value.replace(r"\n", "\n").replace(r"\r", "\n").replace(r"\t", "\t")
    return value


def _count_pdf_pages_rough(data: bytes) -> int:
    raw = data.decode("latin-1", errors="ignore")
    hits = re.findall(r"/Type\s*/Page\b", raw)
    return len(hits) or 1


# --- General PDF text repair (no per-document hardcoding) -------------------
#
# These helpers fix two failure modes that affect ANY PDF of their kind, not a
# single document:
#   1. LaTeX/TeX PDFs (e.g. Beamer) export Romanian diacritics as a base letter
#      plus a SEPARATED spacing/combining mark (`t ¸` -> ț, `a ˘` -> ă,
#      `ˆ ı` -> î). We recombine them using character classes only.
#   2. Slide decks (Beamer/PowerPoint->PDF) emit many physical PDF pages for one
#      logical slide (incremental "overlays"). We detect the standard
#      `current / total` slide-counter footer, collapse each overlay group to its
#      final page, and strip the repeated footer/header boilerplate.
# Nothing here is keyed to a specific title, author, or wording.

_CED = r"[¸̧]"   # cedilla / comma-below (spacing U+00B8 or combining U+0327)
_BRV = r"[˘̆]"   # breve (spacing U+02D8 or combining U+0306)
_CIR = r"[ˆ\^̂]"  # circumflex (spacing U+02C6 / ASCII ^ / combining U+0302)
_CED_MAP = {"s": "ș", "S": "Ș", "t": "ț", "T": "Ț"}
_BRV_MAP = {"a": "ă", "A": "Ă"}
_CIR_MAP = {"a": "â", "A": "Â", "i": "î", "I": "Î", "ı": "î"}


_MATH_OPS = r"[∈∉⊂⊆⊇⊃∪∩≤≥≠≈≡→←↔⇒⇐∧∨±×÷≪≫∼]"
_RO_LOWER = "a-zăâîșț"
_RO_UPPER = "A-ZĂÂÎȘȚ"


def _repair_line(line: str) -> str:
    """Repair one extracted line: recombine TeX diacritics, restore obvious spaces.

    All rules are general (character classes / operators) with no per-document
    word, name, or formula list. TeX accent commands precede their letter, so a
    mark binds to the FOLLOWING base letter first, then the preceding one.
    Circumflex is restricted to a/i because the other circumflex vowels are not
    Romanian letters.
    """
    s = unicodedata.normalize("NFC", line).replace(" ", " ")
    s = re.sub(_CED + r"\s*([sStT])", lambda m: _CED_MAP[m.group(1)], s)
    s = re.sub(_BRV + r"\s*([aA])", lambda m: _BRV_MAP[m.group(1)], s)
    s = re.sub(_CIR + r"\s*([aAiIı])", lambda m: _CIR_MAP[m.group(1)], s)
    s = re.sub(r"([sStT])\s*" + _CED, lambda m: _CED_MAP[m.group(1)], s)
    s = re.sub(r"([aA])\s*" + _BRV, lambda m: _BRV_MAP[m.group(1)], s)
    s = re.sub(r"([aAiIı])\s*" + _CIR, lambda m: _CIR_MAP[m.group(1)], s)
    s = s.replace("ı", "i")  # dotless i -> i once accents resolved
    # Restore spaces TeX dropped — only at unambiguous boundaries:
    s = re.sub(r"\s*(" + _MATH_OPS + r")\s*", r" \1 ", s)            # around operators
    s = re.sub(r":(?=[^\s\d])", ": ", s)                            # colon glued to word
    s = re.sub(r"^(\d{1,2})\.(?=\S)", r"\1. ", s)                   # enumerator "3.x"
    # word glued to the next capitalised word (TeX dropped the inter-word space),
    # e.g. "deComputational" -> "de Computational", "puncteleA" -> "punctele A".
    s = re.sub(r"([" + _RO_LOWER + r"])([" + _RO_UPPER + r"])", r"\1 \2", s)
    # A single italic math letter (a named point/scalar) glued to the following
    # Romanian word — TeX drops the space around math runs: "Bdouă" -> "B două",
    # "Creste" -> "C este", "Pde" -> "P de", "Ași" -> "A și". The trailing token
    # is a closed list of frequent connective words, so capitalised real words
    # ("Computational", "Springer") are never split (their tail is not a word here).
    s = re.sub(
        r"\b([A-Z])(este|sunt|dou[ăa]|de|din|dintre|prin|pentru|care|fie|în|"
        r"fără|astfel|atunci|unde|notat|exist[ăa]|și|sau|aparține|conține)\b",
        r"\1 \2", s)
    # word glued to an opening set-bracket, e.g. "segmentul[AB]" -> "segmentul [AB]".
    # (Round parens are left alone so function/ratio notation like r(A,B) is kept.)
    s = re.sub(r"([" + _RO_LOWER + _RO_UPPER + r"])(\[)", r"\1 \2", s)
    # closing bracket/brace glued to the next word, e.g. "{−1}astfel" -> "{−1} astfel".
    s = re.sub(r"([}\]])([" + _RO_LOWER + _RO_UPPER + r"])", r"\1 \2", s)
    return re.sub(r" {2,}", " ", s).strip()


# A line carrying no letter and no digit is decorative noise from TeX math that
# does not survive text extraction — e.g. the "− − →" strokes of an
# \overrightarrow, lone combining marks, or a stray operator. As a caption under
# the real slide image it only adds clutter, so such lines are dropped. General:
# keyed on "has no alphanumeric", not on any specific glyph or document.
_ALNUM_RE = re.compile(r"[0-9A-Za-zĂÂÎȘȚăâîșț]")


def _repair_pdf_text(text: str) -> str:
    """Repair an extracted page, preserving line/bullet structure for readability."""
    if not text:
        return ""
    lines = [_repair_line(ln) for ln in text.splitlines()]
    # Reshape TeX mathematics that lost its 2-D layout (over-arrows, determinants,
    # sub/superscripts) BEFORE the alnum-less filter so vector strokes can be
    # bound to their point pair instead of silently dropped. General + offline.
    from ..conceptgen.mathtext import normalize_math_text
    repaired = normalize_math_text("\n".join(lines))
    return "\n".join(ln for ln in repaired.splitlines() if ln and _ALNUM_RE.search(ln)).strip()


_SLIDE_FOOTER_RE = re.compile(r"(\d{1,3})\s*/\s*(\d{1,3})\s*$")


def _slide_counter(text: str) -> tuple[int, int] | None:
    """Return (current, total) if the page ends with a `x / y` slide counter."""
    m = _SLIDE_FOOTER_RE.search(text or "")
    if not m:
        return None
    cur, total = int(m.group(1)), int(m.group(2))
    return (cur, total) if 1 <= cur <= total <= 400 else None


def _detect_footer_phrase(page_texts: list[str]) -> str | None:
    """Find a header/footer phrase repeated (before the counter) on most pages.

    Generic: collects the trailing word-runs that sit just before each page's
    `x / y` counter and returns the longest run shared by a majority of pages.
    No document-specific string is assumed.
    """
    from collections import Counter
    counts: Counter[str] = Counter()
    pages_with_counter = 0
    for t in page_texts:
        if _slide_counter(t) is None:
            continue
        pages_with_counter += 1
        pre = _SLIDE_FOOTER_RE.sub("", t).strip()
        words = pre.split()
        for k in range(3, 10):
            if len(words) >= k:
                counts[" ".join(words[-k:])] += 1
    if pages_with_counter < 3:
        return None
    threshold = max(2, pages_with_counter * 0.5)
    candidates = [(ph, c) for ph, c in counts.items() if c >= threshold]
    if not candidates:
        return None
    return max(candidates, key=lambda x: (len(x[0]), x[1]))[0]


def _strip_pdf_boilerplate(text: str, footer: str | None) -> str:
    """Line-aware cleanup: drop the counter/footer line, dedup consecutive lines.

    Beamer repeats the section name then the frame title on adjacent lines
    ("Introducere\\nIntroducere"); consecutive identical lines are dropped.
    """
    out: list[str] = []
    prev = None
    for raw in (text or "").splitlines():
        ln = _SLIDE_FOOTER_RE.sub("", raw).strip()
        if footer and ln.endswith(footer):
            ln = ln[: -len(footer)].strip()
        if not ln:
            continue
        if ln == prev:  # section title == frame title, or any repeated header
            continue
        out.append(ln)
        prev = ln
    return "\n".join(out).strip()


def _collapse_slide_overlays(page_texts: list[tuple[int, str]]):
    """If the PDF looks like a slide deck with overlays, collapse to logical slides.

    Returns (units, meta) where each unit is a logical slide built from the final
    overlay page, or None when the deck does not show the slide-counter pattern.
    """
    grouped: dict[int, list[tuple[int, str]]] = {}
    totals: list[int] = []
    for physical, txt in page_texts:
        sc = _slide_counter(txt)
        if sc is None:
            continue
        grouped.setdefault(sc[0], []).append((physical, txt))
        totals.append(sc[1])
    if len(grouped) < 3 or not totals:
        return None
    # Require a genuine overlay signal: more physical pages than logical slides.
    physical_with_counter = sum(len(v) for v in grouped.values())
    if physical_with_counter < len(grouped) + 2:
        return None

    footer = _detect_footer_phrase([t for _, t in page_texts])
    units: list[dict] = []
    for logical in sorted(grouped):
        variants = sorted(grouped[logical], key=lambda x: x[0])
        physical, txt = variants[-1]  # final overlay = complete slide state
        clean = _strip_pdf_boilerplate(txt, footer) or f"Slide {logical}"
        units.append({
            "logical_slide": logical,
            "physical_page": physical,
            "physical_pages": [p for p, _ in variants],
            "text": clean,
        })
    meta = {
        "logical_slide_count": len(units),
        "physical_pages_collapsed": len(page_texts) - len(units),
        "footer_removed": footer,
        # Maps each logical slide number -> its final physical PDF page (1-based)
        # so the device can render the exact slide with PdfRenderer.
        "pdf_logical_slides": {str(u["logical_slide"]): u["physical_page"] for u in units},
        "is_slide_deck": True,
    }
    return units, meta


def _pdf_page_figures(reader) -> dict[str, list[dict]]:
    """Best-effort: every embedded raster image per PDF page, keyed "page_<n>",
    for Captură mode. Uses pypdf's page.images (each is already a discrete image
    object in the file — no rendering/cropping needed). Budget-gated and fully
    defensive: any failure for a page just skips that page's figures."""
    out: dict[str, list[dict]] = {}
    total = 0
    for i, page in enumerate(reader.pages, start=1):
        figs: list[dict] = []
        try:
            images = list(page.images)
        except Exception:
            continue
        for img in images:
            try:
                blob = img.data
                name = getattr(img, "name", "") or ""
            except Exception:
                continue
            ext = Path(name).suffix.lower()
            ctype = _IMG_CONTENT_TYPE.get(ext, "image/png")
            if not blob or len(blob) > _MAX_SLIDE_IMG_BYTES:
                continue
            if total + len(blob) > _MAX_TOTAL_SLIDE_IMG_BYTES:
                break
            total += len(blob)
            figs.append({"base64": base64.b64encode(blob).decode("ascii"),
                         "content_type": ctype, "x": 0, "y": len(figs)})
        if figs:
            out[f"page_{i}"] = figs
        if total >= _MAX_TOTAL_SLIDE_IMG_BYTES:
            break
    return out


def _extract_pdf_resilient(data: bytes, with_figures: bool = False) -> tuple[str, dict]:
    """Try robust per-page extraction first; if no usable text exists, preserve
    pages as visual units instead of emitting garbage.

    Slide-deck PDFs (Beamer/PowerPoint) are collapsed from physical overlay pages
    to logical slides so the deck is not duplicated 3-12x. Romanian TeX diacritics
    are recombined. Both fixes are general and document-agnostic.
    """
    # Gate on a real PDF signature FIRST: a renamed .txt/.docx or a corrupt file
    # must be rejected honestly, never silently turned into a blank 1-page card.
    if not _looks_like_pdf(data):
        raise ValidationError(
            "file is not a valid PDF",
            code="invalid_pdf",
            public_message="Fișierul nu este un PDF valid. Încarcă un PDF real sau o variantă text.",
            field_name="file",
        )
    # Optional pure-Python path if bundled/available.
    try:
        from pypdf import PdfReader  # type: ignore
        reader = PdfReader(io.BytesIO(data))
        page_texts = []
        pages_with_text = 0
        for i, page in enumerate(reader.pages, start=1):
            try:
                txt = page.extract_text() or ""
            except Exception:
                txt = ""
            txt = _repair_pdf_text(txt)
            if txt:
                pages_with_text += 1
            page_texts.append((i, txt))
        if page_texts:
            if pages_with_text > 0:
                collapsed = _collapse_slide_overlays(page_texts)
                if collapsed is not None:
                    units, cmeta = collapsed
                    text = "\n\n".join(f"Slide {u['logical_slide']}:\n{u['text']}" for u in units)
                    return text, {"extractor": "pypdf_slide_overlay_collapse",
                                  "page_count": len(page_texts),
                                  "pages_with_text": pages_with_text, **cmeta}
                footer = _detect_footer_phrase([t for _, t in page_texts])
                text = "\n\n".join(
                    f"Page {i}:\n{_strip_pdf_boilerplate(txt, footer)}" for i, txt in page_texts)
                pdf_meta = {"extractor": "pypdf", "page_count": len(page_texts),
                            "pages_with_text": pages_with_text, "footer_removed": footer}
                if with_figures:
                    figures = _pdf_page_figures(reader)
                    if figures:
                        pdf_meta["figures"] = figures
                return text, pdf_meta
            # Image/scanned PDF: keep page structure so cardifier can create image cards.
            text = "\n\n".join(f"Page {i}:\n" for i, _ in page_texts)
            return text, {"extractor": "pypdf_visual_fallback", "page_count": len(page_texts), "pages_with_text": 0, "note": "no extractable text; preserve pages as visual units"}
    except Exception:
        pass

    # Legacy best-effort extraction only if it looks sane.
    try:
        text, details = _extract_pdf_best_effort(data)
        # Reject gibberish-like output.
        _allowed = set(",.;:!?()[]{}%+-/*=__'\"&@#€$\n")
        weird = sum(1 for ch in text if not (ch.isalnum() or ch.isspace() or ch in _allowed))
        ratio = weird / max(1, len(text))
        if len(text) >= 40 and ratio < 0.08:
            return text, details
    except Exception:
        pass

    # Last resort: preserve REAL pages as visual units. But a PDF that has the
    # signature yet exposes no genuine page objects and no extractable text is
    # corrupt/empty — reject it rather than emit a blank card.
    page_count = _count_real_pdf_pages(data)
    if page_count <= 0:
        raise ValidationError(
            "PDF has no readable pages or content",
            code="no_readable_content",
            public_message="PDF-ul pare corupt sau gol — nu are pagini citibile. Încarcă un PDF valid.",
            field_name="file",
        )
    text = "\n\n".join(f"Page {i}:\n" for i in range(1, page_count + 1))
    return text, {"extractor": "pdf_visual_fallback", "page_count": page_count, "pages_with_text": 0, "note": "fallback visual page preservation"}
