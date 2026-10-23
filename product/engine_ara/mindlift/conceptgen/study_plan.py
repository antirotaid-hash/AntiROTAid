"""Study pacing + per-material card options (offline, deterministic, no network).

Two jobs, both pure stdlib so they run inside Chaquopy on the phone:

1. ``compute_pacing`` — turn the owner's inputs (average phone minutes per week,
   number of logical units in a course, how many times to repeat each, and the
   exam date) into the interval at which the next logical unit should surface
   while the study mode is active. The clean ARA build has NO usage-stats
   permission, so "how much I'm on the phone" is a value the owner states; the
   interval is expressed in *active-phone* minutes (the mode runs while the
   owner is on the phone), and it tightens automatically as the exam nears.

2. ``material_options`` — given the kind of file a material is (pdf slide deck,
   pptx, word/text, image set …), return the card-appearance choices that
   actually make sense for THAT input, as toggles the owner ticks. The UI shows
   only these; the engine consumes the picked values when building cards.

A "logical unit" is one slide (pptx/pdf), one paragraph (word/text) or one
image (image import). A unit becomes one or more cards.
"""
from __future__ import annotations

from datetime import datetime, timezone


def _parse(dt: str) -> datetime:
    s = (dt or "").strip().replace("Z", "+00:00")
    d = datetime.fromisoformat(s)
    return d if d.tzinfo else d.replace(tzinfo=timezone.utc)


def compute_pacing(*, weekly_phone_minutes: float, num_units: int,
                   repeats: int = 3, exam_iso: str, now_iso: str = "") -> dict:
    """Plan how often to surface the next logical unit.

    interval (in active-phone minutes) = phone time left until the exam divided
    by the number of unit-appearances still owed (units × repeats). The mode's
    timer fires every ``interval_minutes`` of active phone use, so the whole
    course is covered ``repeats`` times right before the exam — aggressively if
    little time remains.
    """
    now = _parse(now_iso) if now_iso else datetime.now(timezone.utc)
    exam = _parse(exam_iso)
    units = max(0, int(num_units))
    reps = max(1, int(repeats))
    appearances = units * reps

    seconds_left = (exam - now).total_seconds()
    days_left = seconds_left / 86_400.0
    daily_phone = max(0.0, weekly_phone_minutes) / 7.0
    phone_minutes_left = daily_phone * max(0.0, days_left)

    if appearances == 0 or phone_minutes_left <= 0:
        interval = 0.0
    else:
        interval = phone_minutes_left / appearances

    # Hard floor: never nag faster than once a minute, even the day before.
    interval_minutes = round(max(1.0, interval), 1) if interval else 0.0
    appearances_per_day = round(appearances / days_left, 1) if days_left > 0 else 0.0
    # Feasible when the computed cadence stays above the 1-minute floor; below it
    # there is more material than phone time can cover at a humane pace.
    feasible = bool(interval and interval >= 1.0)

    return {
        "interval_minutes": interval_minutes,
        "total_appearances": appearances,
        "appearances_per_day": appearances_per_day,
        "days_left": round(days_left, 2),
        "phone_minutes_left": round(phone_minutes_left),
        "daily_phone_minutes": round(daily_phone),
        "repeats": reps,
        "num_units": units,
        "feasible": feasible,
        "exam_passed": seconds_left <= 0,
    }


# --- per-material card-appearance options -------------------------------------
# Each option is a toggle the owner ticks in the material's settings sheet. The
# `key` is what the engine reads; `default` is the algorithm's recommendation
# for that file type; `applies` lists the source kinds it is offered for.

_PDF_KINDS = {"pdf"}
_PPTX_KINDS = {"pptx", "powerpoint", "slides"}
_DOCX_KINDS = {"docx", "word"}
_PROSE_KINDS = {"text", "pasted", "markdown", "html"}
_IMAGE_KINDS = {"image", "images", "photo", "image_zip", "zip"}


def _kind(source_type: str) -> str:
    s = (source_type or "").lower()
    if s in _PDF_KINDS:
        return "pdf"
    if s in _PPTX_KINDS:
        return "pptx"
    if s in _DOCX_KINDS:
        return "docx"
    if s in _IMAGE_KINDS:
        return "image"
    return "prose"


def material_options(source_type: str) -> dict:
    """Return the import SPLIT MODE(S) for this file type (spec §MANDATORY MODES).
    Only PDF gives a real choice (scrollable pages vs paragraphs); every other type
    has a single fixed mode, so the UI shows no mode picker for it. No granularity."""
    kind = _kind(source_type)
    unit_label = {"pdf": "pagină", "pptx": "slide", "docx": "paragraf",
                  "image": "poză", "prose": "paragraf"}[kind]
    if kind == "pdf":
        appearance = {"key": "appearance", "label": "Mod PDF",
                      "choices": ["pdf_as_is", "pdf_paragraphs"], "default": "pdf_as_is"}
    elif kind == "pptx":
        appearance = {"key": "appearance", "label": "Mod", "choices": ["pptx_as_is"], "default": "pptx_as_is"}
    elif kind in ("docx", "prose"):
        appearance = {"key": "appearance", "label": "Mod", "choices": ["docx_paragraphs"], "default": "docx_paragraphs"}
    else:
        appearance = {"key": "appearance", "label": "Mod", "choices": ["image_as_is"], "default": "image_as_is"}
    return {"source_kind": kind, "unit_label": unit_label, "options": [appearance]}


def _OLD_material_options_unused(source_type: str) -> dict:
    kind = _kind(source_type)
    unit_label = {"slides": "slide", "images": "poză", "prose": "paragraf"}.get(kind, "unit")

    # MOD (key kept as "appearance" for wire-compat): what the card shows.
    #   extract  = clean reconstructed text + attached figures + figure refs
    #   as_is    = the original slide/page exactly (faithful image)
    #   capture  = one card per extracted figure (+ caption)
    # Slide decks (incl. PDF) support all three; prose has no image so extract
    # only; a single uploaded image is shown as-is.
    if kind == "slides":
        appearance = {
            "key": "appearance", "label": "Mod",
            "choices": ["extract", "as_is", "capture"],
            "default": "extract",
        }
    elif kind == "images":
        appearance = {
            "key": "appearance", "label": "Mod",
            "choices": ["as_is", "capture"],
            "default": "as_is",
        }
    else:
        appearance = {
            "key": "appearance", "label": "Mod",
            "choices": ["extract"],
            "default": "extract",
        }

    # GRANULARITATE (key kept as "card_size"): how finely a unit is cut, in extract
    # mode. atomic = 1 concept/card; compact = 1 logical unit/card. Not meaningful
    # for an image material (one image = one card).
    options = [appearance]
    if kind != "images":
        options.append({
            "key": "card_size", "label": "Granularitate",
            "choices": ["atomic", "balanced", "compact"],
            # Default = one card per logical unit (whole slide) so dense math/
            # algorithm slides are never chopped. The user can pick finer.
            "default": "compact",
        })
    return {
        "source_kind": kind,
        "unit_label": unit_label,
        "options": options,
    }


# granularity -> max characters per card (the unit chunks when it exceeds this).
# Old size values (small/medium/large) alias onto the new axis so settings saved
# before the redesign keep working.
CARD_SIZE_CHARS = {
    "atomic": 700, "balanced": 750, "compact": 1200,
    "small": 700, "medium": 750, "large": 1200,
}

# Old appearance/size values -> new axis values (back-compat for saved settings).
GRANULARITY_ALIAS = {"small": "atomic", "medium": "balanced", "large": "compact"}
MODE_ALIAS = {
    "image_only": "as_is", "image_plus_caption": "capture",
    "text_plus_image": "extract", "text_only": "extract", "auto": "extract",
}


def resolve_split_mode(source_type: str, value: str) -> str:
    """Resolve a stored/legacy mode value to the canonical SPLIT MODE for this file
    type. Only PDF actually branches (pages vs paragraphs); the rest are fixed."""
    kind = _kind(source_type)
    v = (value or "").lower()
    if kind == "pdf":
        paragraphs = ("paragraph" in v) or v in ("extract", "text_only", "text_plus_image", "pdf_paragraphs")
        return "pdf_paragraphs" if paragraphs else "pdf_as_is"
    if kind == "pptx":
        return "pptx_as_is"
    if kind in ("docx", "prose"):
        return "docx_paragraphs"
    return "image_as_is"


# --- multi-exam scheduling ----------------------------------------------------
# Several exams, each with its own deadline and its own pile of units×repeats of
# study "appearances", competing for the same scarce resource: the minutes the
# owner spends on the phone. Two policies:
#   sequential — study one exam at a time, each inside its own window before its
#                deadline (window = previous deadline .. this deadline).
#   combine    — interleave by Earliest-Deadline-First. Feasible exactly when, at
#                every deadline d, the appearances owed by all exams due by d fit
#                in the phone-time available by d (the classic deadline-capacity
#                / Hall condition). When it does NOT fit (overcommitted) we don't
#                silently drop material: we first shave the repeat count globally
#                (rule a — everything once beats some things 3× and others 0×),
#                and only if a single pass still won't fit do we report a deficit.
# min_interval is the physical floor: we never surface units faster than this, so
# capacity-by-deadline = phone-minutes-by-deadline / min_interval.

_MIN_INTERVAL_MINUTES = 1.0


def _norm_exams(exams, now):
    out = []
    for e in exams or []:
        try:
            d = _parse(e["exam_iso"])
        except Exception:
            continue
        days = (d - now).total_seconds() / 86_400.0
        out.append({
            "id": e.get("id", ""),
            "name": e.get("name", ""),
            "exam_iso": e["exam_iso"],
            "days_left": days,
            "units": max(0, int(e.get("num_units", 0))),
            "repeats_req": max(1, int(e.get("repeats", 3))),
        })
    return out


def _exam_row(it, reps_eff, window_phone_min, min_interval):
    units = it["units"]
    requested = units * it["repeats_req"]
    appearances = units * reps_eff
    cap = window_phone_min / min_interval if min_interval else 0.0
    fully = requested <= cap + 1e-9
    interval = round(window_phone_min / appearances, 1) if appearances else 0.0
    return {
        "id": it["id"], "name": it["name"], "exam_iso": it["exam_iso"],
        "days_left": round(it["days_left"], 2),
        "num_units": units,
        "repeats_requested": it["repeats_req"],
        "repeats_effective": reps_eff,
        "appearances": appearances,
        "interval_minutes": interval,
        "appearances_per_day": round(appearances / it["days_left"], 1) if it["days_left"] > 0 else 0.0,
        "fully_covered": bool(fully),
    }


def plan_multi_exam(*, exams, weekly_phone_minutes, mode="combine",
                    now_iso="", min_interval_minutes=_MIN_INTERVAL_MINUTES) -> dict:
    """Schedule several exams against one weekly phone-time budget."""
    now = _parse(now_iso) if now_iso else datetime.now(timezone.utc)
    daily = max(0.0, weekly_phone_minutes) / 7.0
    items = [e for e in _norm_exams(exams, now) if e["days_left"] > 0]
    items.sort(key=lambda x: x["days_left"])
    warnings = []
    if not items:
        return {"mode": mode, "feasible": True, "daily_phone_minutes": round(daily),
                "exams": [], "warnings": ["Niciun examen viitor."]}

    if mode == "sequential":
        rows = []
        prev_days = 0.0
        for it in items:
            window_days = max(0.0, it["days_left"] - prev_days)
            window_phone = daily * window_days
            cap = window_phone / min_interval_minutes if min_interval_minutes else 0.0
            reps = it["repeats_req"]
            while reps > 1 and it["units"] * reps > cap:
                reps -= 1
            row = _exam_row(it, reps, window_phone, min_interval_minutes)
            if not row["fully_covered"]:
                warnings.append(f"{it['name'] or it['id']}: nu încape — "
                                f"repetări {it['repeats_req']}→{reps}.")
            rows.append(row)
            prev_days = it["days_left"]
        return {"mode": mode, "feasible": all(r["fully_covered"] for r in rows),
                "daily_phone_minutes": round(daily), "exams": rows, "warnings": warnings}

    # combine: largest global repeat cap that keeps every deadline feasible.
    max_req = max(i["repeats_req"] for i in items)

    def feasible_at(cap_r):
        cum = 0.0
        for it in items:
            reps = min(it["repeats_req"], cap_r)
            cum += it["units"] * reps
            capacity = (daily * it["days_left"]) / min_interval_minutes
            if cum > capacity + 1e-9:
                return False
        return True

    cap_r = next((r for r in range(max_req, 0, -1) if feasible_at(r)), None)
    feasible = cap_r is not None
    if cap_r is None:
        cap_r = 1
        warnings.append("Supra-aglomerat: nu acoperi tot nici măcar o dată — "
                        "mărește orele/săpt., scade unități, sau mută un examen.")
    elif cap_r < max_req:
        warnings.append(f"Repetări reduse global la {cap_r} ca să încapă tot înainte de examene.")

    rows = []
    for it in items:
        reps = min(it["repeats_req"], cap_r)
        # combine window for an exam is now .. its own deadline (it shares the
        # timeline with the others; EDF orders who is served at each slot).
        window_phone = daily * it["days_left"]
        row = _exam_row(it, reps, window_phone, min_interval_minutes)
        # in the deficit case a single pass may still not fit by this deadline
        single_cap = window_phone / min_interval_minutes
        if it["units"] > single_cap + 1e-9:
            row["fully_covered"] = False
            warnings.append(f"{it['name'] or it['id']}: prea multe unități pentru timpul rămas.")
        rows.append(row)

    total_per_day = round(sum(r["appearances_per_day"] for r in rows), 1)
    return {
        "mode": mode, "feasible": feasible, "effective_repeats": cap_r,
        "daily_phone_minutes": round(daily), "appearances_per_day_total": total_per_day,
        "exams": rows, "warnings": warnings,
        # EDF runtime policy: at each surfacing, serve the listed exam with the
        # nearest deadline that still owes appearances for the current day.
        "serve_order": [r["id"] for r in rows],
    }
