"""AntiROTAid lesson-cardifier.

This replaces the old question generator for Study/Overlay Mode.
Rule: a card is a consecutive piece of the source unit:
- PPTX/text with slide markers: slide -> one or more card parts
- PDF/text with page markers: page -> one or more card parts
- pasted text: paragraph -> one or more card parts

No generated Q/A, no cloze, no summaries, no invented explanations.
The app may still have a separate quiz mode later, but this module is the
source of truth for the overlay/study cards shown while AntiROTAid Mode runs.
"""
from __future__ import annotations

import hashlib
import re
import unicodedata
from dataclasses import dataclass
from typing import Iterable

try:
    from .model import Card, KnowledgeUnit, AdaptiveDeck
except Exception:  # pragma: no cover - standalone tests can monkeypatch
    Card = KnowledgeUnit = AdaptiveDeck = None  # type: ignore

_MAX_CARD_CHARS = 620
_TARGET_CARD_CHARS = 430
_MIN_USEFUL_CHARS = 18
_INCOMPLETE_FRAGMENT_SENTINEL = "__ANTIROTAID_INCOMPLETE_FRAGMENT__"


def _sanitize_text(s: str) -> str:
    """Drop control/format/private-use/unassigned/surrogate code points that leak
    from PPTX/PDF font glyphs — they render as tofu boxes (□) in cards. Newlines
    and tabs are kept; real letters (incl. math-italic, Greek, diacritics) stay."""
    if not s:
        return s
    keep = []
    for ch in s:
        if ch in ("\n", "\t"):
            keep.append(ch)
            continue
        if unicodedata.category(ch) in ("Cc", "Cf", "Co", "Cs", "Cn"):
            continue
        keep.append(ch)
    return "".join(keep)


def _has_alnum(s: str) -> bool:
    return any(ch.isalnum() for ch in s)

_MARKER_RE = re.compile(
    r"^\s*(?P<label>slide|page|pagina|pagină|pg|paragraph|paragraf|para|image|imagine|photo|poza|poză)\s*"
    r"(?P<num>\d+)?\s*(?P<sep>[:.\-–—)])?\s*(?P<rest>.*)$",
    re.I,
)
_SLIDE_NUM_ONLY_RE = re.compile(r"^\s*\d{1,4}\s*$")
_JUNK_LINE_RE = re.compile(
    r"^\s*(?:ECE\s+\d+|PowerPoint Presentation|Presentation Overview|Slide\s+Credit\s*:|Credit\s*:.*Ranzato\s*$)\s*",
    re.I,
)
_BULLET_RE = re.compile(r"^\s*(?:[•◦▪▫‣⁃*▶►]|[-–—](?=\s)|\d+[.)])\s*")
_SENTENCE_END_RE = re.compile(r"[.!?。！？)](?:\s|$)")
_BAD_GENERATED_Q_RE = re.compile(
    r"^\s*(?:what\s+(?:is|are|does|do|did)|how\s+(?:does|do|would)|why\s+is|"
    r"ce\s+(?:este|sunt|face|fac|are|rol|se\s+intampla|se\s+întâmplă)|"
    r"care\s+este|completeaz[ăa]|fill\s+in|does\s+)\b",
    re.I,
)

_ADMIN_LOGISTICS_RE = re.compile(
    r"\b(?:instructors?|labs?|lectures?:|grading system|final grade|oral exam|submit projects?|team code|"
    r"collaboration policy|plagiarism|extra points|kahoot|website|@|minimum grade|project must|"
    r"faculty of mathematics|university of bucharest|prof\.?\s*phd|"
    # academic-integrity / course-conduct slides are never study material
    r"(?:un)?acceptable\s+(?:code|collaboration|behaviou?r)|academic\s+integrity|code\s+of\s+conduct|honor\s+code)\b",
    re.I,
)


def is_admin_or_logistics_text(text: str) -> bool:
    """Public: does this slide/page text read as course admin / academic-integrity
    boilerplate (plagiarism, acceptable-code, grading, contact)? Used so Captură
    mode can skip figures sitting on policy slides, the same way text mode drops
    those units."""
    return bool(_ADMIN_LOGISTICS_RE.search(text or ""))
_AGENDA_RE = re.compile(r"^\s*(?:plan for today|agenda|outline)\b", re.I)
_DIAGRAM_FRAGMENT_RE = re.compile(
    r"\b(?:x|y|input|output|discrete|continuous|classification|regression|clustering|dimensionality|"
    r"conv\.?|pool\.?|layer|kernel|feature map|activation map|filter|diagram|visual|figure)\b",
    re.I,
)
_TECH_SIGNAL_RE = re.compile(
    r"\b(?:learning|neural|network|gradient|loss|model|convolution|dropout|rnn|lstm|autoencoder|"
    r"transformer|attention|embedding|diffusion|adversarial|regularization|backprop|classification|"
    r"regression|feature|representation|optimizer|training|testing|probability|matrix|vector)\b",
    re.I,
)

@dataclass
class SourceUnit:
    unit_id: str
    label: str
    number: int
    title: str | None
    lines: list[str]
    source_type: str
    # When a PDF/page sentence spans a physical page boundary, the card remains
    # assigned to the dominant/source-start page but records every contributing
    # page. For normal units this is simply [unit_id].
    source_pages: list[str] | None = None


def _fold_space(text: str) -> str:
    text = (text or "").replace("\u00a0", " ")
    text = re.sub(r"[\t\r\f\v]+", " ", text)
    text = re.sub(r" *\n *", "\n", text)
    text = re.sub(r" {2,}", " ", text)
    return text.strip()


def _clean_line(line: str) -> str:
    line = _fold_space(line)
    line = re.sub(r"^[\u2022\u25e6\u25aa\u25ab\u2023\u2043]\s*", "• ", line)
    line = re.sub(r"^[-–—]\s+", "• ", line)
    line = re.sub(r"(?:\s*(?:…|\.\s*\.\s*\.))+\s*$", "", line)
    return line.strip()


def _is_noise_line(line: str) -> bool:
    s = line.strip()
    if not s:
        return True
    if _SLIDE_NUM_ONLY_RE.match(s) or re.fullmatch(r"(?:Slide|Page)\s+\d{1,4}", s, re.I):
        return True
    if _JUNK_LINE_RE.match(s):
        return True
    # Email/URL-only lines are not useful as lesson cards, but mixed lines are kept.
    if re.fullmatch(r"[\w.+-]+@[\w.-]+", s) or re.fullmatch(r"https?://\S+", s):
        return True
    # bullet/dash/ellipsis-only leftovers carry no content
    if re.fullmatch(r"[•\-–—.…\s]*", s):
        return True
    # empty PowerPoint bullet placeholders exported as a bracket token (<None>)
    if re.fullmatch(r"•?\s*<[^>]{0,24}>", s):
        return True
    return False


def _clean_lines(raw_lines: Iterable[str]) -> list[str]:
    out: list[str] = []
    prev = ""
    for raw in raw_lines:
        # preserve author line breaks, but split accidental multi-line chunks too
        for part in str(raw or "").splitlines():
            line = _clean_line(part)
            if _is_noise_line(line):
                continue
            # remove duplicate consecutive headers/slide-credit spam
            if line == prev:
                continue
            out.append(line)
            prev = line
    return _merge_continuations(out)


def _merge_continuations(lines: list[str]) -> list[str]:
    """Join a slide line that is an obvious wrap of the previous one (PPTX text
    boxes break phrases mid-line). Conservative: only when the previous line is
    not a bullet/header and ends mid-phrase, and the next starts lowercase."""
    out: list[str] = []
    for ln in lines:
        if len(out) >= 2 and not _BULLET_RE.match(ln) and not _BULLET_RE.match(out[-1]):
            prev = out[-1]
            if prev and prev[-1:] in (",",) or (prev and prev[-1:].islower()):
                if ln[:1].islower() and not prev.endswith(":"):
                    out[-1] = prev + " " + ln
                    continue
        out.append(ln)
    return out


def _looks_like_title(line: str) -> bool:
    s = line.strip().rstrip(":")
    if not s:
        return False
    words = s.split()
    if len(words) > 11:
        return False
    if _BULLET_RE.match(s):
        return False
    if s.endswith(('.', '?', '!')):
        return False
    # Titles in the provided lectures often are short noun phrases or title case.
    alpha = [c for c in s if c.isalpha()]
    if not alpha:
        return False
    upperish = sum(1 for c in alpha if c.isupper()) / max(1, len(alpha)) > 0.45
    titlecase = sum(1 for w in words if w[:1].isupper()) >= max(1, len(words) // 2)
    return upperish or titlecase or len(words) <= 7


def _split_units_by_markers(text: str, source_type: str) -> list[SourceUnit] | None:
    lines = [ln.rstrip() for ln in (text or "").splitlines()]
    marker_hits = []
    for i, ln in enumerate(lines):
        m = _MARKER_RE.match(ln)
        if not m:
            continue
        label = m.group("label").lower()
        rest = (m.group("rest") or "").strip()
        num = m.group("num")
        sep = m.group("sep")
        # Avoid treating normal sentences/footers beginning with "slide/page" as markers.
        # For PPTX/PDF extraction generated by AntiROTAid, canonical markers always use
        # a separator, e.g. "Slide 12:" or "Page 4:". This prevents body/footer lines
        # such as "Slide 124" from creating fake source units. For pasted text, we
        # still allow "Slide 1" / "Page 1" style markers because users may type them.
        if not num:
            continue
        if source_type in {"slides", "pptx", "powerpoint", "pdf", "image"} and not sep:
            continue
        marker_hits.append((i, label, int(num or len(marker_hits) + 1), rest))

    # Important guardrail for generated PPTX/PDF exports: the extractor injects
    # canonical markers in strict source order: "Slide 1:", "Slide 2:", ... or
    # "Page 1:", "Page 2:", ... . Slide/page bodies can also contain references
    # such as "Slide 124:" or "Page 32:". Those are NOT new units. For generated
    # imports, accept only the expected next marker number. This prevents both
    # duplicate/decreasing markers and absurd forward jumps from splitting one
    # real source unit into fake units. Pasted/manual text remains more permissive.
    if source_type in {"slides", "pptx", "powerpoint", "pdf", "page"} and marker_hits:
        filtered = []
        expected_by_label = {"slide": 1, "page": 1, "pagina": 1, "pagină": 1, "pg": 1}
        for hit in marker_hits:
            _i, label, num, _rest = hit
            key = "slide" if label == "slide" else ("page" if label in {"page", "pagina", "pagină", "pg"} else label)
            if key in {"slide", "page"}:
                expected = expected_by_label[key]
                if num != expected:
                    continue
                expected_by_label[key] = expected + 1
            filtered.append(hit)
        marker_hits = filtered

    if len(marker_hits) < 1:
        return None
    units: list[SourceUnit] = []
    for idx, (line_i, label, num, rest) in enumerate(marker_hits):
        next_i = marker_hits[idx + 1][0] if idx + 1 < len(marker_hits) else len(lines)
        body_lines = []
        if rest:
            body_lines.append(rest)
        body_lines.extend(lines[line_i + 1:next_i])
        clean = _clean_lines(body_lines)
        if not clean:
            units.append(SourceUnit(f"{label}_{num}", label.title(), num, None, [], source_type))
            continue
        title = clean[0] if _looks_like_title(clean[0]) else None
        unit_id = f"{label}_{num}"
        units.append(SourceUnit(unit_id, label.title(), num, title, clean, source_type, [unit_id]))
    return _repair_page_sentence_boundaries(_collapse_animation_units(units))


def _norm_unit_text(u: "SourceUnit") -> str:
    return re.sub(r"[^0-9a-zà-ɏ]+", "", _unit_text(u.lines).lower())


def _collapse_animation_units(units: list["SourceUnit"]) -> list["SourceUnit"]:
    """Collapse consecutive PPTX/slide animation builds (same slide rebuilt frame
    by frame) and exact duplicates into ONE card. Keeps the fullest frame so the
    user never sees the same card twice."""
    visual = {"slides", "pptx", "powerpoint", "pdf", "page", "image"}
    out: list[SourceUnit] = []
    for u in units:
        if out and u.source_type in visual and out[-1].source_type in visual:
            prev = out[-1]
            tu, tp = _norm_unit_text(u), _norm_unit_text(prev)
            same_title = bool(u.title) and (u.title or "") == (prev.title or "")
            exact_dup = bool(tu) and tu == tp
            build = same_title and tu and tp and (tu.startswith(tp) or tp.startswith(tu))
            if exact_dup or build:
                if len(tu) >= len(tp):
                    out[-1] = u
                continue
        out.append(u)
    return out


_PAGE_LABELS = {"page", "pagina", "pagină", "pg"}


def _is_page_unit(unit: SourceUnit) -> bool:
    return unit.label.lower() in _PAGE_LABELS or unit.unit_id.lower().startswith(("page_", "pagina_", "pagină_", "pg_"))


def _ends_complete_text(text: str) -> bool:
    return bool(re.search(r"[.!?][\"'»)’)]*$", (text or "").strip()))


def _word_count(text: str) -> int:
    return len(re.findall(r"[A-Za-zĂÂÎȘȚăâîșț0-9]+", text or ""))


def _looks_like_unfinished_sentence(text: str) -> bool:
    """True for a real sentence fragment, false for a title/label/bullet chip.

    PDF extraction often produces pages ending with half a sentence. At the same
    time, lecture decks contain title-only visual pages such as ``Introducere``
    or ``Convolutional Layer`` with no final period. Those must not be treated as
    broken prose. The predicate/relative-word gate keeps the repair narrow.
    """
    s = re.sub(r"\s+", " ", text or "").strip()
    if not s or _ends_complete_text(s) or _word_count(s) < 6:
        return False
    if _looks_like_title(s) and _word_count(s) <= 8:
        return False
    return bool(re.search(
        r"\b(?:este|sunt|era|erau|prin|care|când|cand|deoarece|pentru|"
        r"is|are|was|were|that|which|because|through|by|from|to|"
        r"transform[ăa]?|transformă|produce|produces|converts?|stores?|"
        r"folosește|foloseste|descrie|explic[ăa])\b",
        s,
        re.I,
    ))


def _starts_like_continuation(text: str) -> bool:
    s = re.sub(r"\s+", " ", text or "").strip()
    if not s:
        return False
    first = s[:1]
    first_word = re.match(r"[A-Za-zĂÂÎȘȚăâîșț]+", s)
    if first.islower():
        return True
    if first_word and first_word.group(0).lower() in {
        "și", "si", "iar", "care", "prin", "pentru", "deoarece",
        "and", "or", "which", "that", "because", "through", "to", "by",
    }:
        return True
    return False


def _split_complete_and_trailing_fragment(text: str) -> tuple[list[str], str]:
    from .reconstruct import smart_split_sentences
    folded = re.sub(r"\s+", " ", text or "").strip()
    if not folded:
        return [], ""
    parts = smart_split_sentences(folded)
    if _ends_complete_text(folded):
        return parts, ""
    if not parts:
        return [], folded
    return parts[:-1], parts[-1].strip()


def _repair_page_sentence_boundaries(units: list[SourceUnit]) -> list[SourceUnit]:
    """Stitch PDF/page sentences cut by physical page boundaries.

    The lean ARA engine is intentionally verbatim: no summaries, no generated Q/A.
    For paginated sources, however, verbatim page chunks can be invalid because a
    page may start or end in the middle of a sentence. This local repair moves
    the opening continuation of page N+1 onto the unfinished sentence from page N
    and removes the raw fragments, so no card starts with ``transformă ...`` or
    ends with ``prin care plantele``.
    """
    if len(units) < 1 or not all(_is_page_unit(u) for u in units):
        return units

    repaired = [SourceUnit(u.unit_id, u.label, u.number, u.title, list(u.lines), u.source_type, list(u.source_pages or [u.unit_id])) for u in units]
    i = 0
    changed = False
    while i < len(repaired):
        cur = repaired[i]
        cur_text = _unit_text(cur.lines)
        if not _looks_like_unfinished_sentence(cur_text):
            i += 1
            continue

        complete_before, trailing = _split_complete_and_trailing_fragment(cur_text)
        if not trailing:
            i += 1
            continue

        # Last page: there is no completion. Drop the fragment; never make a card
        # from a sentence that did not finish in the source material we received.
        if i + 1 >= len(repaired):
            cur.lines = complete_before
            if not complete_before:
                cur.title = _INCOMPLETE_FRAGMENT_SENTINEL
            changed = True
            i += 1
            continue

        nxt = repaired[i + 1]
        next_text = _unit_text(nxt.lines)
        if not _starts_like_continuation(next_text):
            i += 1
            continue

        next_parts, next_trailing = _split_complete_and_trailing_fragment(next_text)
        if next_parts:
            continuation = next_parts[0]
            remaining_next = next_parts[1:]
            # Preserve a trailing incomplete fragment on the next page only if it
            # is not part of this boundary repair. It may be repaired with page N+2.
            if next_trailing:
                remaining_next.append(next_trailing)
        elif next_trailing:
            continuation = next_trailing
            remaining_next = []
        else:
            i += 1
            continue

        stitched = re.sub(r"\s+", " ", f"{trailing} {continuation}").strip()
        cur.lines = complete_before + [stitched]
        cur.source_pages = list(dict.fromkeys((cur.source_pages or [cur.unit_id]) + (nxt.source_pages or [nxt.unit_id])))
        nxt.lines = remaining_next
        changed = True
        i += 1

    return repaired if changed else units


def split_text_into_units(text: str, source_type: str = "pasted") -> list[SourceUnit]:
    """Split plain text into source units without inventing content."""
    text = _fold_space(text)
    if not text:
        return []
    marked = _split_units_by_markers(text, source_type)
    if marked is not None:
        return marked

    # Pasted text: paragraph is the source unit. For pasted lecture dumps that have
    # blank lines, this keeps logical blocks separate. A single paragraph remains one unit.
    raw_paras = [p.strip() for p in re.split(r"\n\s*\n+", text) if p.strip()]
    if len(raw_paras) <= 1:
        raw_paras = [text]
    units: list[SourceUnit] = []
    for i, para in enumerate(raw_paras, start=1):
        lines = _clean_lines(para.splitlines() or [para])
        if not lines:
            continue
        title = lines[0] if _looks_like_title(lines[0]) and len(lines) > 1 else None
        units.append(SourceUnit(f"paragraph_{i}", "Paragraph", i, title, lines, source_type))
    return units


def _unit_text(lines: list[str]) -> str:
    return "\n".join(lines).strip()


def _group_segments(segs: list[str], limit: int) -> list[str]:
    """Pack already-coherent segments (bullets / sentences) into cards up to
    `limit`, but NEVER split a segment. A single over-limit segment becomes its
    own card whole — a slightly long but coherent card beats a card cut through a
    formula."""
    out: list[str] = []
    cur = ""
    for s in segs:
        s = s.strip()
        if not s:
            continue
        add = s if not cur else cur + " " + s
        if not cur or len(add) <= limit:
            cur = add
        else:
            out.append(cur)
            cur = s
    if cur:
        out.append(cur)
    return out


def _split_long_line(line: str, limit: int = _MAX_CARD_CHARS) -> list[str]:
    """Split a long line ONLY at logical boundaries — never mid-token. Order of
    preference: real bullets (▶ / ► / •…) → sentence ends → keep whole. We never
    hard-cut at a character count, so formulas, fractions and citations stay
    intact (the old behaviour chopped `=−1` | `2` apart)."""
    line = line.strip()
    if len(line) <= limit:
        return [line]
    # 1) Break before each in-line bullet glyph, keeping the glyph with its text.
    bullets = [p.strip() for p in re.split(r"\s*(?=(?:▶|►|•|◦|▪|‣|⁃))", line) if p.strip()]
    if len(bullets) > 1:
        return _group_segments(bullets, limit)
    # 2) A single long bullet/line: break only at sentence ends.
    sentences: list[str] = []
    start = 0
    for m in _SENTENCE_END_RE.finditer(line + " "):
        end = m.end()
        sent = line[start:end].strip()
        if sent:
            sentences.append(sent)
        start = end
    if len(sentences) > 1:
        return _group_segments(sentences, limit)
    # 3) No safe boundary — keep the chunk whole rather than cut a formula apart.
    return [line]


def chunk_unit(unit: SourceUnit, max_chars: int = _MAX_CARD_CHARS) -> list[list[str]]:
    """Return consecutive chunks of lines. Never reorders. Never generates."""
    lines = list(unit.lines)
    if not lines:
        return []
    # If a slide/page fits, keep it whole. This is the safest option.
    if len(_unit_text(lines)) <= max_chars:
        return [lines]

    chunks: list[list[str]] = []
    cur: list[str] = []
    cur_len = 0
    # Soft target where we prefer to break at a sentence/non-bullet boundary.
    # Scales with max_chars so a "small" card size actually yields more, shorter
    # cards (~3 per dense slide) instead of one big chunk.
    target = min(_TARGET_CARD_CHARS, int(max_chars * 0.85)) or max_chars

    def flush():
        nonlocal cur, cur_len
        if cur:
            chunks.append(cur)
        cur = []
        cur_len = 0

    for line in lines:
        sublines = _split_long_line(line, max_chars)
        for sub in sublines:
            sub_len = len(sub) + 1
            # Keep bullets/short related lines together until target size. Hard cap max_chars.
            if cur and (cur_len + sub_len > max_chars or (cur_len >= target and not _BULLET_RE.match(sub))):
                flush()
            cur.append(sub)
            cur_len += sub_len
            if cur_len >= max_chars:
                flush()
    flush()
    return chunks


def _title_for(unit: SourceUnit, part_idx: int, part_total: int) -> str:
    base_label = f"{unit.label} {unit.number}"
    if unit.title:
        base_label += f" · {unit.title}"
    if part_total > 1:
        base_label += f" · Part {part_idx}/{part_total}"
    return base_label


def _make_card_id(unit: SourceUnit, idx: int, body: str) -> str:
    h = hashlib.sha1(f"{unit.unit_id}|{idx}|{body}".encode("utf-8")).hexdigest()[:12]
    return f"lesson_{h}"


def _is_visual_source(unit: SourceUnit) -> bool:
    return (
        unit.source_type in {"slides", "pptx", "powerpoint", "pdf", "page", "image"}
        or unit.label.lower() in {"slide", "page", "pagina", "pagină", "pg", "image", "imagine", "photo", "poza", "poză"}
    )


def _is_admin_or_logistics_unit(unit: SourceUnit) -> bool:
    if not _is_visual_source(unit):
        return False
    text = _unit_text(unit.lines)
    if not text.strip():
        return False
    # Avoid turning technical slides into admin just because they mention e.g. accuracy/validation.
    strong = _ADMIN_LOGISTICS_RE.search(text)
    if not strong:
        return False
    # Cover/contact slides often contain technical course title words such as
    # "Deep Learning", but they are still not study material.
    if re.search(r"prof\.?\s*phd|faculty of mathematics|university of bucharest|@", text, re.I) and len(text) < 320:
        return True
    if _TECH_SIGNAL_RE.search(text) and not re.search(r"@|grading system|collaboration policy|plagiarism|submit projects?|team code|oral exam|final grade", text, re.I):
        return False
    return True


def _is_contentful_sentence_line(line: str) -> bool:
    """Heuristic for short slides/pages that are real study content.

    A short slide with one title and two explanatory sentences should remain a
    text card. A short slide with only labels/captions should become slide_image.
    """
    s = line.strip()
    words = re.findall(r"[A-Za-zĂÂÎȘȚăâîșț0-9]+", s)
    if len(words) < 6:
        return False
    if len(s) < 38:
        return False
    if re.search(r"[.!?]$", s):
        return True
    # Some lecture bullets omit final punctuation but are still full statements.
    return bool(re.search(
        r"\b(is|are|was|were|means|describes|explains|contains|uses|happens|"
        r"produce|produces|convert|converts|reduce|reduces|learn|learns|"
        r"este|sunt|înseamnă|inseamna|descrie|explică|explica|folosește|foloseste)\b",
        s,
        re.I,
    ))


def _is_low_information_slide_text(unit: SourceUnit) -> bool:
    if not _is_visual_source(unit):
        return False
    clean = [l.strip() for l in unit.lines if l.strip()]
    text = _unit_text(clean)
    if not clean:
        return True

    # Title + real explanatory sentence(s) should stay text, even if short.
    body_lines = clean[1:] if unit.title and len(clean) > 1 else clean
    contentful_lines = sum(1 for l in body_lines if _is_contentful_sentence_line(l))
    if contentful_lines >= 1 and len(" ".join(body_lines).split()) >= 6:
        return False
    # PDF/page text has no guaranteed slide thumbnail. A short complete sentence
    # extracted from a PDF page is still study text, not an image-required card.
    if unit.source_type in {"pdf", "page"} and any(re.search(r"[.!?]$", l.strip()) and _word_count(l) >= 3 for l in body_lines):
        return False

    if len(clean) == 1 and len(text) <= 110:
        return True
    # Very short PPT/PDF units without real sentence content are usually diagram
    # captions, section cards, repeated animation frames or credit/table-image
    # slides. Render the original slide/page as image instead of weak text.
    if len(clean) <= 3 and len(text) <= 170:
        return True
    # Short diagram label clusters should be rendered as slide images, not as text cards.
    short_lines = sum(1 for l in clean if len(l) <= 18)
    if len(text) <= 140 and short_lines >= max(2, len(clean) // 2):
        return True
    if len(text) <= 180 and _DIAGRAM_FRAGMENT_RE.search(text) and short_lines >= 2:
        return True
    return False


_DIAGRAM_TOKEN_RE = re.compile(
    r"[^\w]*[A-Za-zͰ-Ͽ\U0001D400-\U0001D7FF]"
    r"[\w,.\-=()/+Ͱ-Ͽ\U0001D400-\U0001D7FF]{0,11}\Z"
)


def _is_diagram_token_line(line: str) -> bool:
    """A short standalone formula/diagram label, e.g. ``w1,1`` ``x3`` ``Σ``
    ``q1`` ``𝛼1,2`` ``b1,1=0`` ``W1 =`` ``𝑧𝐿`` — the token noise that leaks when
    a diagram/architecture slide is flattened to text. A line with a real word
    (4+ consecutive letters) is prose, not a bare label."""
    s = line.strip()
    if not s or len(s) > 12:
        return False
    if re.search(r"[A-Za-zĂÂÎȘȚăâîșț]{4,}", s):
        return False
    return bool(_DIAGRAM_TOKEN_RE.match(s))


def _is_fragment_line(line: str) -> bool:
    """A bare ≤3-word label/word-cloud fragment with no sentence structure —
    e.g. ``unsupervised`` ``Mixture of`` ``SIFT/HOG`` ``28x28x1``."""
    s = line.strip()
    if not s or re.search(r"[.!?]$", s):
        return False
    if _is_contentful_sentence_line(s):
        return False
    return len(re.findall(r"\S+", s)) <= 3


def _body_lines(body: str, title: str = "") -> list[str]:
    lines = [l.strip() for l in (body or "").split("\n") if l.strip()]
    if title and lines and lines[0] == title.strip():
        lines = lines[1:]
    return lines


def _is_diagram_token_dump(lines: list[str]) -> bool:
    """Body dominated by short formula/diagram labels (weight matrices ``w1,1``,
    attention tokens ``𝑥1``/``𝑞1``, ``Σ`` nodes) — an unreadable token wall."""
    if len(lines) < 5:
        return False
    tokens = sum(1 for l in lines if _is_diagram_token_line(l))
    return tokens >= 5 and tokens / len(lines) >= 0.55


def _is_fragment_cloud(lines: list[str]) -> bool:
    """Body that is a pure word/label cloud (≥10 short fragments, no real
    sentence, low words-per-line or heavy repetition) — e.g. the
    'Traditional ML vs Deep Learning' figure flattened to ``fixed / unsupervised
    / supervised / classifier`` repeated. These are diagrams, not study text."""
    if len(lines) < 10:
        return False
    if any(_is_contentful_sentence_line(l) for l in lines):
        return False
    frags = [l for l in lines if _is_fragment_line(l)]
    if not frags or len(frags) / len(lines) < 0.85:
        return False
    avg_words = sum(len(re.findall(r"\S+", l)) for l in frags) / len(frags)
    distinct = len({f.lower() for f in frags}) / len(frags)
    return avg_words <= 1.6 or distinct <= 0.7


def is_weak_visual_text(body: str, title: str = "") -> bool:
    """A lesson_chunk whose text is really a diagram/word-cloud and reads as
    noise. The caller should prefer the real slide image *when one exists*
    (decided where media availability is known — the Android bridge). With no
    image, the best-available text card is kept."""
    lines = _body_lines(body, title)
    return _is_diagram_token_dump(lines) or _is_fragment_cloud(lines)


def _is_diagram_label_dump(unit: SourceUnit) -> bool:
    return _is_diagram_token_dump(_body_lines(_unit_text(unit.lines), unit.title or ""))


def _is_title_only_visual(unit: SourceUnit) -> bool:
    # Only slide/image-like source units should become visual cards. Pasted text
    # paragraphs must remain text cards even if they are short or one-line.
    # PPT animation / diagram slides often export only the repeated header
    # (e.g. "Convolutional Layer") while the real content is visual. AntiROTAid
    # keeps those as slide_image cards so Android can render the slide thumbnail.
    clean = [l for l in unit.lines if l.strip()]
    if not _is_visual_source(unit):
        return False
    if unit.source_type == "image" or unit.label.lower() in {"image", "imagine", "photo", "poza", "poză"}:
        return True
    # A TXT/notes file that merely uses "Slide N:" headings has no slide picture
    # to show, so a short slide with real lines under its title (e.g. lab values
    # "BUN: 10-20 mg/dL.") must stay text, not "Visual slide — no extractable text".
    if (unit.source_type not in {"slides", "pptx", "powerpoint", "pdf", "page"}
            and len(clean) > (1 if unit.title else 0)):
        return False
    if _is_low_information_slide_text(unit):
        return True
    return bool(len(clean) == 1 and (unit.title is not None or _looks_like_title(clean[0])))


def _visual_card_for(unit: SourceUnit) -> dict:
    # A title that is itself a diagram token (e.g. "𝑥1") is meaningless as a
    # heading — fall back to the slide label so the chip stays clean.
    safe_title = unit.title if (unit.title and not _is_diagram_token_line(unit.title)) else None
    q = _title_for(SourceUnit(unit.unit_id, unit.label, unit.number, safe_title, unit.lines, unit.source_type), 1, 1)
    if _BAD_GENERATED_Q_RE.match(q):
        q = f"{unit.label} {unit.number}"
    title = safe_title or (unit.lines[0].strip() if unit.lines and not _is_diagram_token_line(unit.lines[0]) else f"{unit.label} {unit.number}")
    body = title.strip() if title else f"{unit.label} {unit.number}"
    # This marker is intentionally explicit: no fake explanation, no fake quiz.
    # It tells the app/user that the source unit is mainly a diagram/image.
    # no fake placeholder text. The native app must render media for slide_image cards.
    body = body
    # Keep the slide's real extracted lines (labels/fragments) so the bridge can
    # fall back to actual on-slide text if no embeddable picture is ever found for
    # this unit (e.g. a native PPT shape/SmartArt diagram, not a raster image) —
    # the user should always see *something* from the slide, never a blank card.
    raw_lines = [l.strip() for l in (unit.lines or []) if l.strip()]
    raw_text_fallback = "\n".join(dict.fromkeys(raw_lines))
    return {
        "card_id": _make_card_id(unit, 1, body),
        "question": q,
        "answer": body,
        "card_type": "slide_image",
        "unit_id": unit.unit_id,
        "concept_ids": [],
        "relation_ids": [],
        "evidence": [title] if title else [],
        "confidence": 1.0,
        "coverage_tags": ["source_unit", "slide_image", "no_generated_question"],
        "raw_text_fallback": raw_text_fallback,
        "media": {
            "type": "image_required",
            "source": "pptx_slide_pdf_page_or_uploaded_image",
            "unit_id": unit.unit_id,
            "source_type": unit.source_type,
            "unit_label": unit.label,
            "unit_number": unit.number,
            "required": True,
        },
        "source": {"type": unit.source_type, "label": unit.label, "number": unit.number, "title": title},
        "source_pages": unit.source_pages or [unit.unit_id],
    }


_NUMBERED_ITEM_RE = re.compile(r"^\s*\(?\d+[.)]\s+\S")


def _split_box_by_levels(bp: list[dict]) -> list[list[dict]]:
    """Split ONE text box into concept subtrees ONLY when it has a real outline:
    ≥2 top-level (lvl 0) paragraphs and at least one of them has indented
    children. A flat bullet list (the common case) stays one concept — we never
    invent a split where the author put none."""
    idx0 = [i for i, p in enumerate(bp) if p.get("lvl", 0) <= 0]
    has_children = any(i + 1 < len(bp) and bp[i + 1].get("lvl", 0) > bp[i].get("lvl", 0) for i in idx0)
    if len(idx0) >= 2 and has_children:
        out: list[list[dict]] = []
        for k, start in enumerate(idx0):
            end = idx0[k + 1] if k + 1 < len(idx0) else len(bp)
            out.append(bp[start:end])
        return out
    return [bp]


def _coalesce_tiny(subs: list[list[dict]]) -> list[list[dict]]:
    """Within ONE box, keep a tiny one-line subgroup (usually a heading like
    "Recommended Setting") attached to the content that follows it, so a header
    never becomes its own fragment card and never bleeds into the previous box."""
    out: list[list[dict]] = []
    carry: list[dict] = []
    for i, s in enumerate(subs):
        cur = carry + s
        if _nonblank([p["text"] for p in cur]) < 2 and i < len(subs) - 1:
            carry = cur
            continue
        out.append(cur)
        carry = []
    if carry:
        if out:
            out[-1] = out[-1] + carry
        else:
            out.append(carry)
    return out


def _structure_concept_groups(structure: list[dict]) -> list[list[str]]:
    """Group a slide's outline into concepts. Primary signal: each separate
    PowerPoint text box is one concept group (authors put each idea in its own
    box). Within a box, split further only on a real outline, and keep tiny
    headers attached to their content (never crossing a box boundary)."""
    body = [p for p in structure if not p.get("is_title")]
    if not body:
        return []
    boxes: list[list[dict]] = []
    cur: list[dict] = []
    cur_box = object()
    for p in body:
        b = p.get("box", 0)
        if b != cur_box and cur:
            boxes.append(cur)
            cur = []
        cur_box = b
        cur.append(p)
    if cur:
        boxes.append(cur)
    groups: list[list[dict]] = []
    for bp in boxes:
        groups.extend(_coalesce_tiny(_split_box_by_levels(bp)))
    return [[p["text"] for p in g] for g in groups]


def _text_concept_groups(lines: list[str]) -> list[list[str]]:
    """Structure-less fallback (PDF prose, pasted text): split a unit only on an
    explicit numbered list (1) … 2) …). Otherwise keep it whole — no false cuts."""
    lines = [l for l in (lines or []) if l.strip()]
    if len(lines) < 2:
        return [lines] if lines else []
    starts = [i for i, l in enumerate(lines) if _NUMBERED_ITEM_RE.match(l)]
    if len(starts) >= 2:
        out: list[list[str]] = []
        if starts[0] > 0:
            out.append(lines[:starts[0]])  # preface before the first item
        for k, start in enumerate(starts):
            end = starts[k + 1] if k + 1 < len(starts) else len(lines)
            out.append(lines[start:end])
        return out
    return [lines]


def _nonblank(lines: list[str]) -> int:
    return sum(1 for l in lines if l.strip())


def _atomic_subunits(unit: SourceUnit, structure: list[dict] | None) -> list[SourceUnit]:
    """Break a unit into one sub-unit per top-level concept (atomic granularity).
    Returns ``[unit]`` unchanged when there is no detectable sub-structure.

    Guard against over-splitting: a slide laid out as a table/columns has many
    one-line text boxes ("Preprocessing", "Association Metric") that are NOT
    standalone concepts. We only split when at least two groups are substantial
    (>= 2 real lines); stray one-line fragments are folded into the group before
    them so they never become their own junk card."""
    groups = _structure_concept_groups(structure) if structure else _text_concept_groups(unit.lines)
    if len(groups) <= 1:
        return [unit]
    # Only split when there are at least two substantial concepts (>= 2 real
    # lines). A table/diagram slide of one-line boxes keeps its whole-slide card.
    if sum(1 for g in groups if _nonblank(g) >= 2) < 2:
        return [unit]
    subs: list[SourceUnit] = []
    for gi, glines in enumerate(groups, start=1):
        clean = _clean_lines(glines)
        if not clean:
            continue
        # Concept name = the group's own header when it reads like one, else the
        # slide title — so each card chip names its concept, not just "Slide N".
        header = clean[0] if _looks_like_title(clean[0]) else None
        subs.append(SourceUnit(
            f"{unit.unit_id}_c{gi}", unit.label, unit.number,
            header or unit.title, clean, unit.source_type, unit.source_pages,
        ))
    return subs or [unit]


def _cards_for_unit(unit: SourceUnit, max_card_chars: int, force_text: bool = False) -> list[dict]:
    """Cards for ONE (possibly concept-sub) unit. Verbatim chunks for textual
    units; an explicit visual card for visual/title-only units.

    ``force_text`` is set for atomic concept sub-units: those are real (often
    short) concept text, so the "short slide => render as image" routing must be
    skipped — otherwise a 2-line concept like "Word Embeddings (Predict)…" would
    wrongly become a picture card."""
    cards: list[dict] = []
    if unit.title == _INCOMPLETE_FRAGMENT_SENTINEL:
        return cards
    # Administrative/course-logistics slides are not study material; keep them out
    # of overlay cards. This prevents cover/contact/grading/plagiarism slides from
    # becoming anti-doomscroll cards.
    if _is_admin_or_logistics_unit(unit):
        return cards
    # Decorative-only units (section dividers like "* * *", a lone "⋯", rule
    # lines) carry no studyable content — never make a card from them.
    _combined = (unit.title or "") + "\n" + "\n".join(unit.lines or [])
    if _combined.strip() and not _has_alnum(_combined):
        return cards
    # Every remaining slide/page/paragraph should produce a card. Textual units produce
    # verbatim chunks; visual/title-only units produce an explicit visual card.
    if not unit.lines:
        cards.append(_visual_card_for(unit))
        return cards
    if not force_text and _is_title_only_visual(unit):
        cards.append(_visual_card_for(unit))
        return cards
    chunks = chunk_unit(unit, max_card_chars)
    total = len(chunks)
    if total == 0:
        cards.append(_visual_card_for(unit))
        return cards
    for i, lines in enumerate(chunks, start=1):
        body = _unit_text(lines)
        if not force_text and total > 1 and len(body) < 90 and unit.source_type in {"slides", "pptx", "powerpoint", "pdf", "page"} and not _TECH_SIGNAL_RE.search(body):
            # Drop leftover footer/credit fragments created after splitting a long slide.
            continue
        if (not force_text and len(body) < _MIN_USEFUL_CHARS and unit.source_type in {"slides", "pptx", "powerpoint", "pdf", "page", "image"}
                and (unit.source_type in {"image", "pdf", "page", "slides", "pptx", "powerpoint"} or _looks_like_title(body))):
            # Tiny title-like slide/page/image bodies are usually visual material.
            # Tiny real sentences remain text cards for pasted/plain text.
            cards.append(_visual_card_for(SourceUnit(unit.unit_id, unit.label, unit.number, unit.title or body, [body], unit.source_type)))
            continue
        q = _title_for(unit, i, total)
        # Guardrail: we never create question prompts. If source title itself
        # is a question from the lecture, that's okay, but our prefix prevents
        # the old generated Q/A look.
        if _BAD_GENERATED_Q_RE.match(q):
            q = f"{unit.label} {unit.number} · Part {i}/{total}"
        cards.append({
            "card_id": _make_card_id(unit, i, body),
            "question": q,
            "answer": body,
            "card_type": "lesson_chunk",
            "unit_id": unit.unit_id,
            "concept_ids": [],
            "relation_ids": [],
            "evidence": [body],
            "confidence": 1.0,
            "coverage_tags": ["source_unit", "verbatim_lesson_chunk"],
            "source": {"type": unit.source_type, "label": unit.label, "number": unit.number, "title": unit.title, "part": i, "parts_total": total},
            "source_pages": unit.source_pages or [unit.unit_id],
        })
    return cards


def cards_from_units(units: list[SourceUnit], max_card_chars: int = _MAX_CARD_CHARS,
                     granularity: str = "compact",
                     structure_by_num: dict | None = None) -> list[dict]:
    """Turn source units into cards. ``granularity``:
    - ``atomic``  → split each unit into one card per top-level CONCEPT (separate
                    PowerPoint text box, or a top-level bullet subtree / numbered
                    item) using the slide outline when available; never invents a
                    split when the unit is a single thought.
    - ``balanced``/``compact`` → keep the unit whole, chunked only by size.
    """
    structure_by_num = structure_by_num or {}
    cards: list[dict] = []
    for unit in units:
        sub_units = [unit]
        if granularity == "atomic":
            st = structure_by_num.get(str(unit.number)) if _is_visual_source(unit) else None
            concepts = _atomic_subunits(unit, st)
            if len(concepts) > 1:
                sub_units = concepts
        force_text = len(sub_units) > 1  # atomic concept text must not be visualised
        for su in sub_units:
            cards.extend(_cards_for_unit(su, max_card_chars, force_text=force_text))
    return cards


def _absorb_title_only_units(units: list[SourceUnit]) -> list[SourceUnit]:
    """Extract mode: a standalone section/title slide ("What is word2vec?") is not
    a card on its own — fold its title into the next unit as a heading so it adds
    context instead of producing a 3-word junk card. Conservative: only units that
    are a single title-like line with a following unit are absorbed."""
    out: list[SourceUnit] = []
    pending: str | None = None
    for unit in units:
        clean = [l for l in unit.lines if l.strip()]
        is_title_only = (_is_visual_source(unit) and len(clean) <= 1
                         and (unit.title is not None or (clean and _looks_like_title(clean[0]))))
        if is_title_only and not _is_diagram_label_dump(unit):
            head = (unit.title or (clean[0] if clean else "")).strip()
            pending = f"{pending} · {head}" if pending and head else (head or pending)
            continue
        if pending:
            unit = SourceUnit(unit.unit_id, unit.label, unit.number,
                              unit.title or pending, [pending] + list(unit.lines),
                              unit.source_type, unit.source_pages)
            pending = None
        out.append(unit)
    if pending:  # trailing section slide with nothing after it — keep it as-is
        out.append(SourceUnit(f"section_{len(out)+1}", "Slide", len(out)+1, pending, [pending], "slides"))
    return out


def build_lesson_page_feed(text: str, *, source_type: str = "pasted", mode: str = "full_course",
                           max_units: int | None = None, max_card_chars: int = _MAX_CARD_CHARS,
                           granularity: str = "compact", structure: dict | None = None,
                           merge_title_only: bool = False) -> dict:
    units = split_text_into_units(_sanitize_text(text or ""), source_type=source_type)
    if merge_title_only:
        units = _absorb_title_only_units(units)
    if max_units is not None:
        units = units[:max_units]
    pages = []
    total_cards = 0
    _prev_body = ""  # drop cards identical to the immediately preceding one
    for unit in units:
        cards = cards_from_units([unit], max_card_chars, granularity=granularity, structure_by_num=structure)
        deduped = []
        for c in cards:
            body = (c.get("answer") or "").strip()
            if body and body == _prev_body:
                continue
            deduped.append(c)
            if body:
                _prev_body = body
        cards = deduped
        if not cards:
            continue
        total_cards += len(cards)
        pages.append({
            "page_id": unit.unit_id,
            "title": unit.title,
            "kind": "lesson_chunk",
            "source_unit_type": unit.label.lower(),
            "status": "ok" if cards else "not_enough_material",
            "reason": None if cards else "empty_or_visual_unit_without_extractable_text",
            "cards": cards,
            "quiz": [],
            "understanding_action": {
                "label": "Am înțeles" if _looks_ro(text) else "I understood",
                "next": "next_source_unit",
            },
        })
    return {
        "mode": mode,
        "generator": "unit_based_verbatim_lesson_cards_v1",
        "lang": "ro" if _looks_ro(text) else "en",
        "pages": pages,
        "page_count": len(pages),
        "sections_total": len(units),
        "card_count": total_cards,
        "notes": [
            "Study/Overlay cards are consecutive pieces of the source unit. No generated Q/A, cloze, or summaries.",
            f"Generated {total_cards} lesson cards from {len(units)} source units.",
        ],
    }


def _looks_ro(text: str) -> bool:
    return bool(re.search(r"[ăâîșțĂÂÎȘȚ]|\b(este|sunt|care|pentru|prin|dintre|deoarece|învăț|invat|lecție|lectie)\b", text or "", re.I))


def build_lesson_deck(text: str, *, source_type: str = "pasted", mode: str = "full_course", max_units: int | None = None):
    """Return AdaptiveDeck with lesson_chunk Cards for persistence APIs."""
    units0 = split_text_into_units(text or "", source_type=source_type)
    if max_units is not None:
        units0 = units0[:max_units]
    card_dicts = cards_from_units(units0)
    if Card is None or KnowledgeUnit is None or AdaptiveDeck is None:
        return {"cards": card_dicts}
    k_units = []
    for u in units0:
        body = _unit_text(u.lines)
        k_units.append(KnowledgeUnit(
            unit_id=u.unit_id, title=u.title, source_type=u.source_type,
            kind="lesson_chunk", raw_text=body, clean_text=body,
            length=len(body.split()), density=1.0 if body else 0.0,
            importance=1.0 if body else 0.0, decorative=False,
        ))
    cards = [Card(**d) for d in card_dicts]
    deck = AdaptiveDeck(
        cards=cards, units=k_units, concepts=[], mode=mode,
        sections_total=len(units0), sections_processed=len(units0),
        important_concepts=0,
        notes=["AntiROTAid lesson-cardifier: cards are verbatim source chunks, not generated questions."],
    )
    return deck
