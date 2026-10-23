"""Lesson completion/check-off logic for AntiROTAid.

The user must be able to mark lessons as done, but the app should also keep a
local progress model:
- imported lessons
- card count
- seen cards
- understood cards
- checked lessons
- last local mastery score

This module intentionally has no cloud dependency.
"""
from __future__ import annotations

from typing import Any
from .engagement import analyze_session, recommend_next_actions


def _cards_from_feed(feed: dict[str, Any]) -> list[dict[str, Any]]:
    if "pages" in feed:
        cards = []
        for page in feed.get("pages", []):
            cards.extend(page.get("cards", []))
        return cards
    return list(feed.get("cards", []))


def build_initial_lesson_progress(lesson_id: str, title: str, feed: dict[str, Any]) -> dict[str, Any]:
    cards = _cards_from_feed(feed)
    return {
        "lesson_id": lesson_id,
        "title": title,
        "cards_total": len(cards),
        "cards_seen": 0,
        "cards_understood": 0,
        "coverage": 0.0,
        "avg_mastery": 0.0,
        "checked": False,
        "checked_at_ms": None,
        "last_actions": [],
        "local_only": True,
    }


def update_lesson_progress(progress: dict[str, Any], feed: dict[str, Any], events: list[dict[str, Any]]) -> dict[str, Any]:
    analysis = analyze_session(feed, events)
    out = dict(progress)
    out.update({
        "cards_total": analysis["cards_total"],
        "cards_seen": analysis["cards_seen"],
        "cards_understood": analysis["cards_understood"],
        "coverage": analysis["coverage"],
        "avg_mastery": analysis["avg_mastery"],
        "needs_review_count": len(analysis["needs_review_card_ids"]),
        "too_fast_count": len(analysis["too_fast_card_ids"]),
        "last_actions": recommend_next_actions(analysis),
    })
    return out


def mark_lesson_checked(progress: dict[str, Any], *, timestamp_ms: int, force: bool = False) -> dict[str, Any]:
    """Mark lesson as checked.

    Default rule:
      - allow check when coverage >= 0.75 and avg_mastery >= 0.55
      - force=True allows manual override; keep audit flag local.
    """
    can_check = force or (progress.get("coverage", 0.0) >= 0.75 and progress.get("avg_mastery", 0.0) >= 0.55)
    out = dict(progress)
    if can_check:
        out["checked"] = True
        out["checked_at_ms"] = int(timestamp_ms)
        out["manual_override"] = bool(force and not (progress.get("coverage", 0.0) >= 0.75 and progress.get("avg_mastery", 0.0) >= 0.55))
    else:
        out["checked"] = False
        out["check_blocked_reason"] = "coverage_or_mastery_too_low"
    return out
