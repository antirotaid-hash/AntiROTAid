"""AntiROTAid Study/Overlay feed.

Source of truth: Study Mode cards are source-unit lesson cards, not generated
questions. Card content is a consecutive chunk of the uploaded slide/page/
paragraph, or a slide_image card for visual-only units.
"""
from __future__ import annotations

from .lesson_cardifier import build_lesson_page_feed, _MAX_CARD_CHARS


def build_page_feed(text: str, *, source_type: str = "pasted", mode: str = "full_course",
                    max_units: int | None = None, max_card_chars: int = _MAX_CARD_CHARS,
                    granularity: str = "compact", structure: dict | None = None,
                    merge_title_only: bool = False) -> dict:
    """Return a JSON-able source-unit feed for overlay/study mode."""
    return build_lesson_page_feed(text or "", source_type=source_type, mode=mode,
                                  max_units=max_units, max_card_chars=max_card_chars,
                                  granularity=granularity, structure=structure,
                                  merge_title_only=merge_title_only)
