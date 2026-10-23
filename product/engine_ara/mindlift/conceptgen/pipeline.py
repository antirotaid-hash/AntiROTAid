"""AntiROTAid deck facade.

Study/Overlay Mode must not synthesize Q/A cards. This facade delegates to the
unit-based lesson cardifier so persisted decks and overlay feeds share one
contract.
"""
from __future__ import annotations

from .lesson_cardifier import build_lesson_deck


def generate_adaptive_deck(text: str, *, source_type: str = "pasted", mode: str = "full_course", weak_keys: set[str] | None = None, max_units: int | None = None):
    return build_lesson_deck(text or "", source_type=source_type, mode=mode, max_units=max_units)
