"""AntiROTAid Android bridge (lean, offline, no MindLift MEGA engine).

Mirrors the `{ok, api_version, data}` envelope contract the Kotlin `EngineBridge`
expects, but dispatches only to the product's lesson/behavior/progress modules.
Stateless and functional: Kotlin passes JSON in, gets JSON out, and persists the
small progress object locally. Nothing leaves the device.
"""
from __future__ import annotations

import json
import re

from .core.errors import ValidationError

API_VERSION = "1"


def _ok(data: dict) -> str:
    return json.dumps({"ok": True, "api_version": API_VERSION, "data": data},
                      ensure_ascii=False)


def _err(code: str, message: str, *, recoverable: bool = True) -> str:
    return json.dumps({"ok": False, "api_version": API_VERSION,
                       "error": {"code": code, "message": message,
                                 "recoverable": recoverable}}, ensure_ascii=False)


class Bridge:
    """One bridge instance per app process. db_path is accepted for contract
    compatibility but unused — the lean product keeps no SQLite DB."""

    def __init__(self, db_path: str = ":memory:", env_file: str = "",
                 ai_provider: str = "") -> None:
        self.db_path = db_path  # kept for interface parity; unused

    def close(self) -> None:
        return None

    # --- generic dispatch ---------------------------------------------------
    def call(self, method: str, args_json: str = "{}") -> str:
        try:
            kwargs = json.loads(args_json) if args_json else {}
            if not isinstance(kwargs, dict):
                return _err("bad_args", "Some of that input could not be used as given.")
            fn = _DISPATCH.get(method)
            if fn is None:
                return _err("unknown_method", "That action is not available.")
            return _ok(fn(**kwargs))
        except ValidationError as exc:
            # A file/input the user gave us failed real validation. Surface the
            # STABLE code so Android can localize the message, plus a Romanian
            # public fallback. Recoverable: the user can pick another file.
            return _err(exc.code, exc.public_message, recoverable=True)
        except TypeError as exc:
            return _err("bad_args", f"[DBG] {exc}")
        except Exception as exc:  # never cross JNI with a live exception
            return _err("engine_error", f"[DBG] {type(exc).__name__}: {exc}",
                        recoverable=True)

    def doctor(self) -> str:
        # On-device smoke check: prove the lean engine imports and produces cards.
        try:
            from .conceptgen.feed import build_page_feed
            feed = build_page_feed("Slide 1: Test\nThis is a short demo lesson body.",
                                   source_type="slides")
            cards = sum(len(p.get("cards", [])) for p in feed.get("pages", []))
            ok_cards = cards >= 1
        except Exception:
            ok_cards = False
        return json.dumps({
            "ok": True, "api_version": API_VERSION,
            "data": {
                "checks": [
                    {"name": "bridge", "ok": True},
                    {"name": "lesson_engine", "ok": ok_cards},
                    {"name": "mode", "ok": True, "detail": "offline_only"},
                ],
                "passed": 2 + int(ok_cards), "failed": 0 if ok_cards else 1,
                "offline_only": True,
            },
        }, ensure_ascii=False)


# --- product methods (the only API the AntiROTAid UI needs) -----------------

def _card_chars(card_size: str) -> int:
    from .conceptgen.study_plan import CARD_SIZE_CHARS
    from .conceptgen.lesson_cardifier import _MAX_CARD_CHARS
    return CARD_SIZE_CHARS.get((card_size or "").lower(), _MAX_CARD_CHARS)


def _lesson_feed(*, text: str = "", source_type: str = "pasted",
                 mode: str = "full_course", max_units=None, card_size: str = "") -> dict:
    from .conceptgen.feed import build_page_feed
    return build_page_feed(text or "", source_type=source_type, mode=mode,
                           max_units=max_units, max_card_chars=_card_chars(card_size))


def _lesson_feed_file(*, filename: str, payload: str = "", encoding: str = "base64",
                      mode: str = "full_course", max_units=None, local_path: str = "",
                      include_media: bool = True, card_size: str = "",
                      appearance: str = "auto") -> dict:
    from .ingestion.file_import import extract_file
    # When no inline payload is sent, read the file straight from its on-device
    # path. This avoids base64-encoding a 20-60MB deck in Java just to count or
    # list its cards (that round-trip allocated tens of MB and could OOM).
    if not payload and local_path:
        with open(local_path, "rb") as _fh:
            extracted = extract_file(filename, _fh.read(), encoding="bytes", with_media=include_media)
    else:
        extracted = extract_file(filename, payload, encoding=encoding, with_media=include_media)
    # extract_file returns a FileExtractionResult dataclass (text/source_type/metadata).
    text = getattr(extracted, "text", "") or ""
    stype = getattr(extracted, "source_type", "pasted") or "pasted"
    meta = getattr(extracted, "metadata", {}) or {}
    # Resolve the SPLIT MODE for this file type (spec): only PDF branches
    # (pdf_as_is | pdf_paragraphs); pptx/docx/image have one fixed mode.
    from .conceptgen.study_plan import resolve_split_mode
    split_mode = resolve_split_mode(stype, appearance)

    # PDF AS-IS: one scrollable card per PHYSICAL page (rendered on device). No
    # text extraction — the page itself is the card.
    if split_mode == "pdf_as_is":
        feed = _pdf_as_is_feed(meta, local_path)
        feed["import_metadata"] = {k: meta.get(k) for k in ("filename", "extension", "bytes") if k in meta}
        feed["split_mode"] = split_mode
        feed["import_report"] = {"cards_created": sum(len(p.get("cards", [])) for p in feed.get("pages", [])),
                                 "page_count": meta.get("page_count")}
        return feed

    from .conceptgen.feed import build_page_feed
    # pptx AS-IS = each slide is one card (no concept split, every slide kept);
    # paragraphs modes = one card per paragraph. Always whole-unit ("compact").
    feed = build_page_feed(text, source_type=stype, mode=mode, max_units=max_units,
                           max_card_chars=_card_chars("compact"),
                           granularity="compact", structure=None,
                           merge_title_only=False)
    if meta.get("media"):
        media_map = meta["media"]  # slide images keyed by "slide_<n>" / source ref
        feed["media"] = media_map
        from .conceptgen.lesson_cardifier import is_weak_visual_text
        # Attach the real picture to each slide_image card by its source unit
        # number, so the app renders the diagram instead of a placeholder.
        for page in feed.get("pages", []):
            for card in page.get("cards", []):
                ctype = card.get("card_type")
                if ctype == "slide_image":
                    cm = card.get("media", {}) or {}
                    num = cm.get("unit_number")
                    label = (cm.get("unit_label") or "slide").lower()
                    key = f"{label}_{num}"
                    img = media_map.get(key) or media_map.get(f"slide_{num}")
                    if img and img.get("base64"):
                        cm["base64"] = img["base64"]
                        cm["content_type"] = img.get("content_type", "image/png")
                        cm["required"] = False
                        card["media"] = cm
                    else:
                        # No embeddable picture for this slide (e.g. a native PPT
                        # shape/SmartArt diagram, not a raster image) — never leave
                        # the card both imageless AND textless. Show the slide's
                        # real extracted lines instead of a blank placeholder.
                        fallback = (card.get("raw_text_fallback") or "").strip()
                        if fallback:
                            card["card_type"] = "lesson_chunk"
                            card["answer"] = fallback
                            card["coverage_tags"] = list(card.get("coverage_tags") or []) + ["visual_text_fallback"]
                elif ctype == "lesson_chunk":
                    # Diagram/word-cloud slides flatten to unreadable token/word
                    # walls. When the slide has a real embedded image, show that
                    # instead of the noise text; with no image we keep the text.
                    uid = str(card.get("unit_id", ""))
                    img = media_map.get(uid)
                    if not (img and img.get("base64")):
                        continue
                    title = (card.get("source", {}) or {}).get("title") or ""
                    if not is_weak_visual_text(card.get("answer", ""), title):
                        continue
                    card["card_type"] = "slide_image"
                    card["coverage_tags"] = ["source_unit", "slide_image", "weak_text_to_image"]
                    # The image already shows every label/word — drop the noisy
                    # word-cloud text so it doesn't reappear as a caption (the UI
                    # shows a caption only when body differs from the title).
                    card["answer"] = title
                    card["media"] = {
                        "base64": img["base64"],
                        "content_type": img.get("content_type", "image/png"),
                        "type": "image_required",
                        "unit_id": uid,
                        "required": False,
                    }
    # Slide-deck PDFs (Beamer/LaTeX, PowerPoint->PDF): attach the rendered PDF page
    # to each card so the exact, perfectly-typeset slide is available on device
    # (PdfRenderer, offline). We do NOT force every card to an image: the math-text
    # normaliser now reconstructs the extracted text (vec(AP), ℝ², determinants,
    # de-glued words), so the readable caption IS shown as the primary content.
    # Only genuinely visual slides — the diagram/word-cloud units the cardifier
    # already marked slide_image (weak/near-empty text) — render image-first; for
    # those the attached PDF page is what gets drawn.
    slide_pages = meta.get("pdf_logical_slides") or {}
    if local_path and meta.get("is_slide_deck") and slide_pages:
        for page in feed.get("pages", []):
            for card in page.get("cards", []):
                unit_id = str(card.get("unit_id", ""))
                m = re.search(r"(\d+)$", unit_id)
                if not m:
                    continue
                phys = slide_pages.get(m.group(1))
                if not phys:
                    continue
                cm = card.get("media", {}) or {}
                cm["document_path"] = local_path
                cm["document_type"] = "pdf"
                cm["physical_page"] = phys
                cm["required"] = False
                card["media"] = cm
    # If the imported file itself is a SINGLE image, make the image card display
    # the actual local file. A ZIP of images already carries each picture as
    # base64 per card, so never overwrite those with the (zip) local_path.
    if local_path and stype == "image":
        for page in feed.get("pages", []):
            for card in page.get("cards", []):
                if card.get("card_type") == "slide_image" and not (card.get("media") or {}).get("base64"):
                    media = card.setdefault("media", {})
                    media["local_path"] = local_path
                    media["type"] = media.get("type") or "image_required"
                    media["required"] = True

    feed["import_metadata"] = {k: meta.get(k) for k in ("filename", "extension", "bytes") if k in meta}
    feed["split_mode"] = split_mode
    # Partial-success import report (spec §7): how many cards were created and what
    # was skipped, so the library can show "42 carduri create, 2 problematice".
    cards_created = sum(len(p.get("cards", [])) for p in feed.get("pages", []))
    report = {"cards_created": cards_created}
    for k in ("images_used", "images_skipped", "slides_used", "slide_count",
              "empty_or_visual_slides_preserved", "paragraphs", "tables", "page_count"):
        if k in meta:
            report[k] = meta[k]
    feed["import_report"] = report
    return feed


def _pdf_as_is_feed(meta: dict, local_path: str) -> dict:
    """PDF AS-IS (spec §2): one scrollable visual card per PHYSICAL page. The card
    carries the on-device document path + page number; Android renders the page at
    full width and scrolls it. No text extraction — the page IS the card."""
    n = int(meta.get("page_count") or 0)
    pages = []
    for i in range(1, n + 1):
        cid = f"pdf_page_{i}"
        media = {"document_type": "pdf", "physical_page": i, "type": "image_required", "required": True}
        if local_path:
            media["document_path"] = local_path
        pages.append({
            "page_id": cid,
            "title": f"Pagina {i}",
            "kind": "slide_image",
            "source_unit_type": "page",
            "status": "ok",
            "cards": [{
                "card_id": cid,
                "question": f"Pagina {i}",
                "answer": "",
                "card_type": "slide_image",
                "unit_id": cid,
                "concept_ids": [], "relation_ids": [], "evidence": [],
                "confidence": 1.0,
                "coverage_tags": ["pdf_page_as_is"],
                "media": media,
                "source": {"type": "pdf", "label": "Page", "number": i, "title": ""},
                "source_pages": [cid],
            }],
            "quiz": [],
            "understanding_action": {"label": "Am parcurs", "next": "next_source_unit"},
        })
    return {"mode": "pdf_as_is", "generator": "pdf_as_is_pages",
            "pages": pages, "page_count": n, "card_count": n}


_FIGURE_REF_RE = re.compile(r"\b(?:fig(?:ure|ura|\.)?|imagine[ae]?)\s*(\d{1,2})\b", re.I)


def _attach_figure_references(feed: dict, media_map: dict) -> None:
    """Extract mode: when a card's text says "Figure N" / "Figura N", attach the
    image of the slide/page it lives on so the reader sees the picture next to the
    prose that mentions it. ``media_map`` is the lean one-image-per-slide map that
    was already loaded — no extra figures are decoded for this."""
    if not media_map:
        return
    for page in feed.get("pages", []):
        for card in page.get("cards", []):
            if card.get("card_type") != "lesson_chunk" or card.get("media", {}).get("base64"):
                continue
            m = _FIGURE_REF_RE.search(card.get("answer") or "")
            if not m:
                continue
            src = card.get("source") or {}
            key = f"{(src.get('label') or 'slide').lower()}_{src.get('number')}"
            img = media_map.get(key) or media_map.get(f"slide_{src.get('number')}")
            if img and img.get("base64"):
                card["media"] = {
                    "base64": img["base64"],
                    "content_type": img.get("content_type", "image/png"),
                    "type": "image_reference",
                    "figure_number": int(m.group(1)),
                    "required": False,
                }
                card["coverage_tags"] = list(card.get("coverage_tags") or []) + ["figure_reference"]


def _capture_feed(feed: dict, meta: dict, local_path: str) -> dict:
    """Capture mode: rebuild the feed as one card per extracted figure, in source
    order. Each figure becomes a slide_image card captioned by its slide/page
    title. Falls back to the original feed when no figures were extracted."""
    figures = meta.get("figures") or {}
    if not figures:
        return feed
    # title per source unit, from the text feed we already built
    title_by_key: dict[str, str] = {}
    for page in feed.get("pages", []):
        for card in page.get("cards", []):
            src = card.get("source") or {}
            k = f"{(src.get('label') or 'slide').lower()}_{src.get('number')}"
            t = (src.get("title") or page.get("title") or "").strip()
            if t and k not in title_by_key:
                title_by_key[k] = t
    # Caption fallback: a figure on a slide with no title (so the text feed had
    # nothing) still gets a label from the slide's own outline — its title line, or
    # its first body line — instead of an empty caption or a bare "Slide N".
    structure = meta.get("slide_structure") or {}
    from .conceptgen.lesson_cardifier import is_admin_or_logistics_text
    admin_keys: set[str] = set()
    for num_str, paras in structure.items():
        key = f"slide_{num_str}"
        slide_text = "\n".join(p.get("text", "") for p in paras)
        # Skip figures sitting on academic-integrity / course-admin slides
        # (plagiarism, acceptable-code, grading) — never study material.
        if is_admin_or_logistics_text(slide_text):
            admin_keys.add(key)
            continue
        if title_by_key.get(key):
            continue
        title_para = next((p["text"] for p in paras if p.get("is_title")), "")
        body_first = next((p["text"] for p in paras if not p.get("is_title")), "")
        cap = (title_para or body_first or "").strip()
        if cap:
            title_by_key[key] = cap[:80]
    pages = []
    count = 0
    for key in sorted(figures, key=_figure_key_order):
        if key in admin_keys:
            continue
        figs = figures[key]
        label, _, num = key.partition("_")
        for fi, fig in enumerate(figs, start=1):
            count += 1
            caption = title_by_key.get(key, "")
            cid = f"capture_{key}_{fi}"
            has_img = bool(fig.get("base64"))
            pages.append({
                "page_id": cid,
                "title": caption or f"{label.title()} {num}",
                "kind": "slide_image",
                "source_unit_type": label,
                "status": "ok",
                "cards": [{
                    "card_id": cid,
                    "question": f"{label.title()} {num}" + (f" · {caption}" if caption else ""),
                    "answer": caption,
                    "card_type": "slide_image",
                    "unit_id": key,
                    "concept_ids": [], "relation_ids": [], "evidence": [],
                    "confidence": 1.0,
                    "coverage_tags": ["figure", "capture_mode"],
                    "media": {
                        **({"base64": fig["base64"]} if has_img else {}),
                        "content_type": fig.get("content_type", "image/png"),
                        "type": "image_required",
                        "unit_id": key,
                        "required": True,
                    },
                    "source": {"type": "image", "label": label, "number": _safe_int(num), "title": caption},
                }],
                "quiz": [],
                "understanding_action": feed.get("pages", [{}])[0].get("understanding_action")
                if feed.get("pages") else {"label": "OK", "next": "next_source_unit"},
            })
    if not pages:
        return feed
    feed["pages"] = pages
    feed["page_count"] = len(pages)
    feed["card_count"] = count
    return feed


def _figure_key_order(key: str):
    m = re.search(r"(\d+)$", key)
    return int(m.group(1)) if m else 0


def _safe_int(s: str) -> int:
    try:
        return int(s)
    except (TypeError, ValueError):
        return 0


def _analyze_session(*, feed: dict, events: list) -> dict:
    from .behavior.engagement import analyze_session, recommend_next_actions
    analysis = analyze_session(feed or {}, events or [])
    return {"analysis": analysis, "actions": recommend_next_actions(analysis)}


def _init_progress(*, lesson_id: str, title: str, feed: dict) -> dict:
    from .behavior.progress import build_initial_lesson_progress
    return build_initial_lesson_progress(lesson_id, title, feed or {})


def _update_progress(*, progress: dict, feed: dict, events: list) -> dict:
    from .behavior.progress import update_lesson_progress
    return update_lesson_progress(progress or {}, feed or {}, events or [])


def _check_lesson(*, progress: dict, timestamp_ms: int, force: bool = False) -> dict:
    from .behavior.progress import mark_lesson_checked
    return mark_lesson_checked(progress or {}, timestamp_ms=int(timestamp_ms),
                               force=bool(force))


def _material_options(*, source_type: str = "") -> dict:
    from .conceptgen.study_plan import material_options
    return material_options(source_type)


def _pacing(*, weekly_phone_minutes: float, num_units: int, repeats: int = 3,
            exam_iso: str, now_iso: str = "") -> dict:
    from .conceptgen.study_plan import compute_pacing
    return compute_pacing(weekly_phone_minutes=weekly_phone_minutes, num_units=num_units,
                          repeats=repeats, exam_iso=exam_iso, now_iso=now_iso)


def _study_plan(*, exams: list, weekly_phone_minutes: float, mode: str = "combine",
                now_iso: str = "") -> dict:
    from .conceptgen.study_plan import plan_multi_exam
    return plan_multi_exam(exams=exams or [], weekly_phone_minutes=weekly_phone_minutes,
                           mode=mode, now_iso=now_iso)


_DISPATCH = {
    "lesson_feed": _lesson_feed,
    "lesson_feed_file": _lesson_feed_file,
    "analyze_session": _analyze_session,
    "init_progress": _init_progress,
    "update_progress": _update_progress,
    "check_lesson": _check_lesson,
    "material_options": _material_options,
    "pacing": _pacing,
    "study_plan": _study_plan,
}


def make_bridge(db_path: str = ":memory:", env_file: str = "",
                ai_provider: str = "") -> Bridge:
    return Bridge(db_path, env_file, ai_provider)
