"""Regression tests for AntiROTAid ARA page-boundary sentence repair."""
from __future__ import annotations

from mindlift.conceptgen.feed import build_page_feed


def _cards(feed):
    return [c for page in feed["pages"] for c in page["cards"]]


def test_pdf_pages_are_independent_units():
    # The live ara engine intentionally treats each PDF page as its own unit
    # (cross-page sentence stitching from ChatGPT's overfit variant was rejected
    # — see memory project_pdf_beamer_fix). A complete sentence on a page becomes
    # a proper lesson_chunk card scoped to that page.
    text = (
        "Page 1:\n"
        "Fotosinteza este procesul prin care plantele\n"
        "Page 2:\n"
        "transformă lumina în energie chimică. Clorofila absoarbe lumina."
    )
    feed = build_page_feed(text, source_type="pdf")
    cards = _cards(feed)
    assert cards, "expected at least one card"
    # The complete-sentence page 2 yields a text card scoped to page_2 only.
    p2 = [c for c in cards if c.get("source_pages") == ["page_2"]]
    assert p2 and p2[0]["card_type"] == "lesson_chunk"
    assert "energie chimică" in p2[0]["answer"]
    # No card spans both pages (no cross-page stitching).
    assert not any(set(c.get("source_pages", [])) >= {"page_1", "page_2"} for c in cards)


def test_uncompleted_final_page_fragment_makes_no_card():
    text = "Page 1:\nFotosinteza este procesul prin care plantele transformă energia"
    feed = build_page_feed(text, source_type="pdf")
    assert _cards(feed) == []


def test_short_complete_pdf_sentence_stays_text_not_image():
    feed = build_page_feed("Page 1:\nClorofila absoarbe lumina.", source_type="pdf")
    cards = _cards(feed)
    assert cards
    assert cards[0]["card_type"] == "lesson_chunk"
