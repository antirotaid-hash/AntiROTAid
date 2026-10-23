"""Local/offline behavioral analysis for AntiROTAid.

Purpose
-------
AntiROTAid should adapt from what the user does locally on the phone:
- dwell time per card
- inner-card scroll depth
- repeated visits
- skip velocity
- explicit "I understood" / "Nu știu încă"
- lesson completion/check-off

No network. No identity tracking. No cloud analytics.
The app stores only local event records and derived local scores.
"""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from typing import Any
import math
import time

_CARD_EVENTS = {
    "card_shown",
    "card_hidden",
    "inner_scroll",
    "outer_swipe_next",
    "outer_swipe_prev",
    "mark_understood",
    "mark_not_understood",
    "lesson_checked",
    "pause",
    "exit",
}


def _clamp(x: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, x))


def normalize_event(event: dict[str, Any]) -> dict[str, Any]:
    """Return a sanitized local event dict.

    Accepted event fields:
      lesson_id, card_id, event_type, timestamp_ms, dwell_ms,
      visible_ratio, inner_scroll_depth, outer_index, source, meta.

    Unknown fields are intentionally dropped so the local log stays minimal.
    """
    now = int(time.time() * 1000)
    event_type = str(event.get("event_type") or "").strip()
    if event_type not in _CARD_EVENTS:
        event_type = "card_shown"

    def as_int(name: str, default: int = 0) -> int:
        try:
            return int(event.get(name, default) or default)
        except Exception:
            return default

    def as_float(name: str, default: float = 0.0) -> float:
        try:
            return float(event.get(name, default) or default)
        except Exception:
            return default

    return {
        "lesson_id": str(event.get("lesson_id") or "lesson"),
        "card_id": str(event.get("card_id") or "card"),
        "event_type": event_type,
        "timestamp_ms": as_int("timestamp_ms", now),
        "dwell_ms": max(0, as_int("dwell_ms", 0)),
        "visible_ratio": _clamp(as_float("visible_ratio", 1.0)),
        "inner_scroll_depth": _clamp(as_float("inner_scroll_depth", 0.0)),
        "outer_index": max(0, as_int("outer_index", 0)),
        "source": str(event.get("source") or "overlay"),
        "meta": event.get("meta") if isinstance(event.get("meta"), dict) else {},
    }


def _expected_read_ms(card: dict[str, Any]) -> int:
    text = f"{card.get('question','')} {card.get('answer','')}"
    chars = len(text.strip())
    if card.get("card_type") == "slide_image":
        # Images need enough time for visual inspection.
        return 6500
    # ~900 chars/min comfortable on phone, with floor/ceiling.
    return int(max(3500, min(22000, chars / 900 * 60_000)))


def aggregate_card_events(events: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    """Aggregate raw local events by card_id."""
    stats: dict[str, dict[str, Any]] = defaultdict(lambda: {
        "shown": 0,
        "hidden": 0,
        "swipe_next": 0,
        "swipe_prev": 0,
        "understood": 0,
        "not_understood": 0,
        "inner_scroll_events": 0,
        "max_inner_scroll_depth": 0.0,
        "dwell_ms": 0,
        "last_timestamp_ms": 0,
    })
    for raw in events:
        e = normalize_event(raw)
        s = stats[e["card_id"]]
        t = e["event_type"]
        if t == "card_shown":
            s["shown"] += 1
        elif t == "card_hidden":
            s["hidden"] += 1
            s["dwell_ms"] += e["dwell_ms"]
        elif t == "outer_swipe_next":
            s["swipe_next"] += 1
        elif t == "outer_swipe_prev":
            s["swipe_prev"] += 1
        elif t == "mark_understood":
            s["understood"] += 1
        elif t == "mark_not_understood":
            s["not_understood"] += 1
        elif t == "inner_scroll":
            s["inner_scroll_events"] += 1
            s["max_inner_scroll_depth"] = max(s["max_inner_scroll_depth"], e["inner_scroll_depth"])
        s["last_timestamp_ms"] = max(s["last_timestamp_ms"], e["timestamp_ms"])
    return dict(stats)


def _score_card(card: dict[str, Any], stat: dict[str, Any]) -> dict[str, Any]:
    expected = _expected_read_ms(card)
    dwell = int(stat.get("dwell_ms", 0))
    shown = int(stat.get("shown", 0))
    understood = int(stat.get("understood", 0))
    not_understood = int(stat.get("not_understood", 0))
    scroll_depth = float(stat.get("max_inner_scroll_depth", 0.0))
    card_type = card.get("card_type")

    read_ratio = _clamp(dwell / max(1, expected))
    if card_type == "lesson_chunk" and len(str(card.get("answer",""))) > 380:
        # Long text cards should be internally scrolled or read long enough.
        completion_signal = max(read_ratio, scroll_depth)
    else:
        completion_signal = read_ratio

    revisit_signal = _clamp((shown - 1) / 3)
    struggle = 0.0
    if not_understood:
        struggle += 0.45
    if shown >= 3 and understood == 0:
        struggle += 0.20
    if dwell > expected * 2.2 and understood == 0:
        struggle += 0.20
    if completion_signal < 0.35 and int(stat.get("swipe_next", 0)) > 0:
        struggle += 0.25

    explicit_bonus = 0.35 if understood else 0.0
    mastery = _clamp(0.15 + 0.55 * completion_signal + explicit_bonus - 0.45 * _clamp(struggle))
    needs_review = mastery < 0.62 or not_understood > 0
    too_fast = completion_signal < 0.35 and int(stat.get("swipe_next", 0)) > 0

    return {
        "card_id": card.get("card_id"),
        "card_type": card_type,
        "expected_read_ms": expected,
        "dwell_ms": dwell,
        "read_ratio": round(read_ratio, 3),
        "completion_signal": round(completion_signal, 3),
        "shown": shown,
        "revisit_signal": round(revisit_signal, 3),
        "max_inner_scroll_depth": round(scroll_depth, 3),
        "understood": understood,
        "not_understood": not_understood,
        "mastery": round(mastery, 3),
        "needs_review": needs_review,
        "too_fast": too_fast,
        "struggle_score": round(_clamp(struggle), 3),
    }


def analyze_session(deck_or_feed: dict[str, Any], events: list[dict[str, Any]]) -> dict[str, Any]:
    """Analyze a local study session.

    Input can be:
      - feed JSON with pages[*].cards[*]
      - deck JSON with cards[*]

    Output is local-only and safe to store on-device.
    """
    cards: list[dict[str, Any]] = []
    if "pages" in deck_or_feed:
        for page in deck_or_feed.get("pages", []):
            cards.extend(page.get("cards", []))
    else:
        cards = list(deck_or_feed.get("cards", []))

    by_card = aggregate_card_events(events)
    card_scores = []
    for c in cards:
        stat = by_card.get(c.get("card_id"), {})
        card_scores.append(_score_card(c, stat))

    seen = sum(1 for s in card_scores if s["shown"] > 0 or s["dwell_ms"] > 0)
    understood = sum(1 for s in card_scores if s["understood"] > 0)
    needs_review = [s["card_id"] for s in card_scores if s["needs_review"] and (s["shown"] > 0 or s["dwell_ms"] > 0)]
    too_fast = [s["card_id"] for s in card_scores if s["too_fast"]]
    avg_mastery = sum(s["mastery"] for s in card_scores) / max(1, len(card_scores))

    return {
        "cards_total": len(cards),
        "cards_seen": seen,
        "cards_understood": understood,
        "coverage": round(seen / max(1, len(cards)), 3),
        "avg_mastery": round(avg_mastery, 3),
        "needs_review_card_ids": needs_review,
        "too_fast_card_ids": too_fast,
        "attention_flags": {
            "rapid_skips": len(too_fast),
            "many_reviews_needed": len(needs_review) >= max(3, len(cards) // 4),
        },
        "card_scores": card_scores,
    }


def recommend_next_actions(analysis: dict[str, Any]) -> list[dict[str, Any]]:
    """Generate deterministic local recommendations."""
    actions: list[dict[str, Any]] = []
    if analysis.get("coverage", 0) < 0.75:
        actions.append({
            "type": "continue_batch",
            "priority": 1,
            "reason": "not_enough_cards_seen",
            "message": "Continuă batch-ul curent înainte să marchezi lecția ca terminată.",
        })
    if analysis.get("too_fast_card_ids"):
        actions.append({
            "type": "slow_down",
            "priority": 2,
            "card_ids": analysis["too_fast_card_ids"][:5],
            "reason": "rapid_skips",
            "message": "Ai trecut prea repede peste unele carduri. Repetă-le o dată.",
        })
    if analysis.get("needs_review_card_ids"):
        actions.append({
            "type": "review_cards",
            "priority": 3,
            "card_ids": analysis["needs_review_card_ids"][:10],
            "reason": "low_mastery_or_not_understood",
            "message": "Repetă cardurile unde ai stat puțin sau ai marcat că nu știi încă.",
        })
    if not actions:
        actions.append({
            "type": "mark_lesson_ready",
            "priority": 10,
            "reason": "coverage_and_mastery_ok",
            "message": "Lecția poate fi bifată ca parcursă.",
        })
    return actions
