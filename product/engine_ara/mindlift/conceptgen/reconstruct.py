"""Sentence-aware page reconstruction (offline, deterministic, no network).

PDF pages routinely cut a sentence in half: page N ends with
``"Fotosinteza este procesul prin care plantele transformă energia"`` and page
N+1 begins ``"luminoasă în energie chimică stocată în glucoză."``. The product
rule is *no complete sentence = no card*, so without reconstruction BOTH pages
look like fragments and yield nothing.

This layer:
  * splits page-marked material into ordered page units;
  * splits each page into sentences with an abbreviation/decimal-aware splitter
    (so "etc.", "Fig.", "e.g.", "3.14", "1.2. Metodologie" never split wrongly);
  * stitches a trailing fragment on page N onto the continuation that opens page
    N+1, producing ONE complete sentence;
  * records ``source_pages`` (every page the sentence drew from) and a
    ``dominant_page`` (the page that contributed the most characters);
  * drops a trailing fragment that never completes (no card from a fragment).

Pure stdlib; the page feed groups the reconstructed sentences by dominant page
and generates cards only from complete sentences.
"""
from __future__ import annotations

import re

_NUL = "\x00"

# single-word abbreviations whose trailing dot is NOT a sentence end
_ABBR = ("etc", "fig", "nr", "art", "vs", "dr", "prof", "no", "pp", "cf", "ed",
         "al", "ex", "cap", "vol", "pag", "par", "ec", "lit", "alin", "ed",
         "sec", "ch", "vezi", "cca")
# abbreviations that contain internal dots
_ABBR_DOTTED = ("e.g", "i.e", "a.m", "p.m", "ș.a", "s.a", "î.e.n", "d.e.n")

# a page header line: "Page 1:", "Pagina 2.", "Page 3 -", "Pagina 5"
_PAGE_HEADER = re.compile(
    r"^\s*(?:page|pagina)\s*(\d+)?\s*[:.\-)]?\s*(.*)$", re.I)
_PAGE_KEYWORD = re.compile(r"^\s*(?:page|pagina)\b\s*(?:\d+\s*[:.\-)]?|[:.\-)])",
                           re.I)


def has_page_markers(text: str) -> bool:
    """True when the material is explicitly paginated (PDF-style ``Page N:`` /
    ``Pagina N:`` headers) and therefore may split sentences across pages."""
    return any(_PAGE_KEYWORD.match(l) for l in (text or "").splitlines())


def _looks_like_title(s: str) -> bool:
    """A short, punctuation-free phrase after a page marker is the page TITLE; a
    longer phrase or one with sentence punctuation is body content. If the inline
    marker is followed by a predicate fragment ("Page 1: Photosynthesis transforms"),
    it is body text, not a title; this is common in copied PDF page text."""
    s = s.strip()
    if not s or len(s.split()) > 6 or re.search(r"[.!?]", s):
        return False
    sf = s.lower()
    predicate_words = (
        " transform", " transforms", " transformă", " transforma", " produce", " produces",
        " stochează", " stocheaza", " transmite", " transmit", " calculează", " calculeaza",
        " măsoară", " masoara", " formează", " formeaza", " absoarbe", " scade", " crește", " creste",
        " updates", " computes", " stores", " maps", " filters", " lowers", " increases", " decreases",
    )
    return not any(p in f" {sf}" for p in predicate_words)


def split_into_pages(text: str) -> list[tuple[str, str, str]]:
    """Return ordered ``(page_id, title, body_text)`` for page-marked material.

    An inline title after the marker ("Pagina 4: Revoluția Industrială") is
    extracted as ``title`` and NOT left in the body, so it can't be glued onto the
    first sentence ("Revoluția Industrială Revoluția Industrială ..."). A long
    phrase after the colon is treated as body, not a title. Content before the
    first header is ignored (cover/preamble)."""
    lines = (text or "").splitlines()
    headers = [i for i, l in enumerate(lines) if _PAGE_KEYWORD.match(l)]
    if not headers:
        return [("page_001", None, text or "")]
    pages: list[tuple[str, str, str]] = []
    for k, start in enumerate(headers):
        end = headers[k + 1] if k + 1 < len(headers) else len(lines)
        m = _PAGE_HEADER.match(lines[start])
        num = m.group(1) if m and m.group(1) else None
        trailing = (m.group(2).strip() if m else "")
        if _looks_like_title(trailing):
            title = trailing
            body_lines = lines[start + 1:end]
        else:
            title = None
            body_lines = ([trailing] if trailing else []) + lines[start + 1:end]
        page_id = f"page_{int(num):03d}" if num else f"page_{k + 1:03d}"
        pages.append((page_id, title, "\n".join(body_lines).strip()))
    return pages


def smart_split_sentences(text: str) -> list[str]:
    """Sentence split that respects abbreviations, decimals and section numbers."""
    s = re.sub(r"\s+", " ", text or "").strip()
    if not s:
        return []
    # protect decimal points (3.14) and section dots (1.2 -> 1<NUL>2)
    s = re.sub(r"(?<=\d)\.(?=\d)", _NUL, s)
    # protect the trailing dot of a multi-part number ("1.2." in "1.2. Metodologie")
    s = re.sub(r"(\d" + _NUL + r"\d+)\.", r"\1" + _NUL, s)
    # protect dotted abbreviations (e.g., i.e.)
    for ab in _ABBR_DOTTED:
        s = re.sub(re.escape(ab) + r"\.", ab.replace(".", _NUL) + _NUL, s, flags=re.I)
    # protect single-word abbreviations ("etc.", "Fig.")
    for ab in _ABBR:
        # Protect abbreviation dots inside a sentence ("Fig. 2", "etc. ceva"),
        # but let them end a sentence when the next token starts uppercase
        # ("etc. Elevii...").
        s = re.sub(r"\b(" + re.escape(ab) + r")\.(?!\s+(?-i:[A-ZĂÂÎȘȚ]))",
                   r"\1" + _NUL, s, flags=re.I)
    parts = re.split(r"(?<=[.!?])\s+", s)
    return [p.replace(_NUL, ".").strip() for p in parts if p.replace(_NUL, ".").strip()]


def _ends_complete(text: str) -> bool:
    return bool(re.search(r"[.!?][\"'»)\]]*$", text.strip()))


def reconstruct(pages: list[tuple[str, str]], *, return_leftover: bool = False):
    """Stitch sentences across page boundaries.

    Returns ordered sentence units::

        {"sentence_id", "text", "source_pages": [...], "dominant_page": "..."}

    A sentence that spans pages lists every contributing page in ``source_pages``
    and is assigned to the page that contributed the most characters. A trailing
    fragment that never completes is dropped (no card from a fragment)."""
    out: list[dict] = []
    carry = ""
    carry_contrib: dict[str, int] = {}
    sid = 0
    for page_id, text in pages:
        text = re.sub(r"\s+", " ", text or "").strip()
        if not text:
            continue
        ends_complete = _ends_complete(text)
        parts = smart_split_sentences(text)
        if not parts:
            continue
        for i, part in enumerate(parts):
            part_len = len(part)
            if i == 0 and carry:
                cur = (carry + " " + part).strip()
                contrib = dict(carry_contrib)
                contrib[page_id] = contrib.get(page_id, 0) + part_len
                carry, carry_contrib = "", {}
            else:
                cur = part
                contrib = {page_id: part_len}
            is_last = i == len(parts) - 1
            if is_last and not ends_complete:
                carry, carry_contrib = cur, contrib            # continues next page
                continue
            sid += 1
            out.append({
                "sentence_id": f"s_{sid:03d}",
                "text": cur,
                "source_pages": list(contrib.keys()),
                "dominant_page": max(contrib, key=contrib.get),
            })
    # a leftover carry never completed -> intentionally produces no card
    leftover = None
    if carry:
        leftover = {"dominant_page": max(carry_contrib, key=carry_contrib.get),
                    "source_pages": list(carry_contrib.keys())}
    if return_leftover:
        return out, leftover
    return out
