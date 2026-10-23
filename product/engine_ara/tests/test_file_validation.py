"""Phase 6 file-validation tests for the AntiROTAid (ara) engine.

The engine must NOT accept a file just because of its extension. Each importer
proves the bytes are the claimed format and carry usable content, and rejects
corrupt/fake/empty files with a stable machine `code` (which Android localizes).

These are real, executed unit tests (pytest) over pure stdlib code — no network,
no device. They cover items #18-29 of the Android Update 1 test plan.
"""
from __future__ import annotations

import base64
import io
import json
import struct
import zipfile
import zlib

import pytest

from mindlift.android_bridge import make_bridge
from mindlift.core.errors import ValidationError
from mindlift.ingestion.file_import import extract_file


# --- helpers to build minimal REAL office/image files -----------------------

def _docx(paragraphs) -> bytes:
    ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    body = "".join(f"<w:p><w:r><w:t>{p}</w:t></w:r></w:p>" for p in paragraphs)
    document = (
        f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        f'<w:document xmlns:w="{ns}"><w:body>{body}</w:body></w:document>'
    )
    content_types = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/word/document.xml" '
        'ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>'
    )
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml", content_types)
        zf.writestr("_rels/.rels", "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'/>")
        zf.writestr("word/document.xml", document)
    return buf.getvalue()


def _pptx(slide_texts) -> bytes:
    a = "http://schemas.openxmlformats.org/drawingml/2006/main"
    p = "http://schemas.openxmlformats.org/presentationml/2006/main"
    content_types = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="xml" ContentType="application/xml"/></Types>'
    )
    presentation = f'<?xml version="1.0"?><p:presentation xmlns:p="{p}"/>'
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml", content_types)
        zf.writestr("ppt/presentation.xml", presentation)
        for i, text in enumerate(slide_texts, start=1):
            slide = (
                f'<?xml version="1.0"?><p:sld xmlns:p="{p}" xmlns:a="{a}"><p:cSld><p:spTree>'
                f'<p:sp><p:txBody><a:p><a:r><a:t>{text}</a:t></a:r></a:p></p:txBody></p:sp>'
                f'</p:spTree></p:cSld></p:sld>'
            )
            zf.writestr(f"ppt/slides/slide{i}.xml", slide)
    return buf.getvalue()


def _png(w=4, h=4) -> bytes:
    def chunk(typ, data):
        return (struct.pack(">I", len(data)) + typ + data
                + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF))
    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    raw = b"".join(b"\x00" + b"\x00\x00\x00" * w for _ in range(h))
    idat = zlib.compress(raw)
    return sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")


def _real_pdf() -> bytes:
    """A tiny but structurally valid one-page PDF with selectable text."""
    objs = [
        b"1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj",
        b"2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj",
        b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R"
        b"/Resources<</Font<</F1 5 0 R>>>>>>endobj",
        b"4 0 obj<</Length 68>>stream\n"
        b"BT /F1 18 Tf 72 700 Td (Fotosinteza este un proces biologic.) Tj ET\n"
        b"endstream endobj",
        b"5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj",
    ]
    out = b"%PDF-1.4\n"
    for o in objs:
        out += o + b"\n"
    out += b"trailer<</Root 1 0 R/Size 6>>\n%%EOF"
    return out


def _import(filename, data):
    return extract_file(filename, data, encoding="bytes")


# =========================================================================
# PDF  (#18 invalid, #19 empty, #20 valid)
# =========================================================================

def test_invalid_pdf_is_rejected():
    fake = b"This is just a text file pretending to be a PDF." * 5
    with pytest.raises(ValidationError) as ei:
        _import("fake.pdf", fake)
    assert ei.value.code == "invalid_pdf"


def test_empty_pdf_is_rejected():
    # Has the %PDF signature + EOF but zero real page objects and no text.
    empty = b"%PDF-1.4\n% no pages here at all\ntrailer<<>>\n%%EOF"
    with pytest.raises(ValidationError) as ei:
        _import("empty.pdf", empty)
    assert ei.value.code in ("invalid_pdf", "no_readable_content")


def test_valid_pdf_imports():
    res = _import("lectie.pdf", _real_pdf())
    assert res.source_type == "pdf"
    assert res.text and res.text.strip()


# =========================================================================
# DOCX  (#21 invalid, #22 valid)
# =========================================================================

def test_invalid_docx_is_rejected():
    with pytest.raises(ValidationError) as ei:
        _import("fake.docx", b"not a zip at all, just bytes")
    assert ei.value.code in ("invalid_docx", "corrupt_file")


def test_zip_without_docx_parts_is_rejected():
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("random.txt", "hello")
    with pytest.raises(ValidationError) as ei:
        _import("fake.docx", buf.getvalue())
    assert ei.value.code == "invalid_docx"


def test_valid_docx_imports():
    res = _import("lectie.docx", _docx([
        "Fotosinteza transforma lumina in energie chimica.",
        "Clorofila absoarbe lumina solara.",
    ]))
    assert res.text and "Fotosinteza" in res.text


# =========================================================================
# PPTX  (#23 invalid, #24 valid)
# =========================================================================

def test_invalid_pptx_is_rejected():
    with pytest.raises(ValidationError) as ei:
        _import("fake.pptx", b"definitely not a pptx")
    assert ei.value.code in ("invalid_pptx", "corrupt_file")


def test_zip_without_pptx_parts_is_rejected():
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("random.txt", "hello")
    with pytest.raises(ValidationError) as ei:
        _import("fake.pptx", buf.getvalue())
    assert ei.value.code == "invalid_pptx"


def test_valid_pptx_imports():
    res = _import("curs.pptx", _pptx(["Retele neuronale", "Straturi convolutionale"]))
    assert res.source_type == "slides"
    assert "Retele neuronale" in res.text


# =========================================================================
# Images  (#25 invalid, #26 valid)
# =========================================================================

def test_invalid_image_is_rejected():
    with pytest.raises(ValidationError) as ei:
        _import("fake.png", b"this is not a png, just text bytes")
    assert ei.value.code == "invalid_image"


def test_empty_image_is_rejected():
    with pytest.raises(ValidationError) as ei:
        _import("empty.jpg", b"")
    assert ei.value.code in ("invalid_image", "empty_document")


def test_valid_png_imports():
    res = _import("diagrama.png", _png(8, 6))
    assert res.source_type == "image"
    assert res.metadata.get("image_width") == 8
    assert res.metadata.get("image_height") == 6


# =========================================================================
# TXT  (#27 empty rejected, #28 valid)
# =========================================================================

def test_empty_txt_is_rejected():
    with pytest.raises(ValidationError) as ei:
        _import("gol.txt", b"   \n\t  \r\n ")
    assert ei.value.code == "empty_txt"


def test_valid_txt_imports():
    res = _import("nota.txt", "Fotosinteza este un proces biologic important.".encode("utf-8"))
    assert res.text.strip()


# =========================================================================
# Oversized  (#29)  — the bridge surfaces a localizable file_too_large code
# =========================================================================

def test_oversized_file_is_rejected_with_size_code():
    # Force the extracted-text cap (cheaper than a 160MB upload) — same code path
    # and the same stable code the UI maps to a localized "file too large" string.
    from mindlift.ingestion import file_import as fi
    big = ("x" * (fi._MAX_EXTRACTED_CHARS + 10)).encode("utf-8")
    with pytest.raises(ValidationError) as ei:
        _import("mare.txt", big)
    assert ei.value.code == "file_too_large"


def test_bridge_surfaces_validation_code_and_message():
    """The Kotlin layer relies on error.code (to localize) + error.message."""
    bridge = make_bridge()
    payload = base64.b64encode(b"not a real pdf").decode("ascii")
    out = json.loads(bridge.call("lesson_feed_file", json.dumps(
        {"filename": "fake.pdf", "payload": payload, "encoding": "base64"})))
    assert out["ok"] is False
    assert out["error"]["code"] == "invalid_pdf"
    assert out["error"]["message"]  # non-empty Romanian fallback
