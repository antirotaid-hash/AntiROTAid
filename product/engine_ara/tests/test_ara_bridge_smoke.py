"""End-to-end offline smoke test for the AntiROTAid (ara) engine bridge.

Exercises the full public bridge API the Android app calls: import -> cards ->
progress -> check -> planning. Everything here is pure local Python (no network,
no DB, no external service), which is exactly the ara runtime contract.
"""
from __future__ import annotations

import base64
import io
import json
import zipfile

from mindlift.android_bridge import make_bridge


def _call(bridge, method, **kwargs):
    raw = bridge.call(method, json.dumps(kwargs))
    out = json.loads(raw)
    assert out.get("ok") is True, f"{method} failed: {raw}"
    return out["data"]


def _cards(feed):
    return [c for page in feed.get("pages", []) for c in page.get("cards", [])]


def _make_docx(paragraphs):
    """Build a minimal valid .docx (OOXML zip) from a list of paragraphs."""
    ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    body = "".join(
        f'<w:p><w:r><w:t>{p}</w:t></w:r></w:p>' for p in paragraphs
    )
    document = (
        f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        f'<w:document xmlns:w="{ns}"><w:body>{body}</w:body></w:document>'
    )
    content_types = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/word/document.xml" '
        'ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
        '</Types>'
    )
    rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" '
        'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" '
        'Target="word/document.xml"/></Relationships>'
    )
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml", content_types)
        zf.writestr("_rels/.rels", rels)
        zf.writestr("word/document.xml", document)
    return buf.getvalue()


def test_doctor_reports_offline_ok():
    bridge = make_bridge()
    data = json.loads(bridge.doctor())["data"]
    assert data["offline_only"] is True
    assert data["failed"] == 0
    assert any(c["name"] == "lesson_engine" and c["ok"] for c in data["checks"])


def test_lesson_feed_text_produces_cards():
    bridge = make_bridge()
    text = (
        "Slide 1: Fotosinteza\n"
        "Fotosinteza este procesul prin care plantele transformă lumina în energie chimică.\n"
        "Slide 2: Clorofila\n"
        "Clorofila este pigmentul verde care absoarbe lumina solară."
    )
    feed = _call(bridge, "lesson_feed", text=text, source_type="slides")
    cards = _cards(feed)
    assert len(cards) >= 1


def test_import_docx_then_progress_lifecycle():
    bridge = make_bridge()
    docx = _make_docx([
        "Fotosinteza este procesul prin care plantele transformă lumina în energie chimică.",
        "Clorofila este pigmentul verde care absoarbe lumina solară.",
        "Reacțiile de lumină au loc în membrana tilacoidă a cloroplastului.",
    ])
    payload = base64.b64encode(docx).decode("ascii")
    feed = _call(bridge, "lesson_feed_file", filename="lectie.docx",
                 payload=payload, encoding="base64")
    cards = _cards(feed)
    assert len(cards) >= 1, "import produced no cards"

    # init -> update (mark first card seen) -> check lesson
    progress = _call(bridge, "init_progress", lesson_id="L1", title="Lecție", feed=feed)
    assert progress.get("total_cards", len(cards)) >= 1

    first_id = cards[0].get("id") or cards[0].get("card_id") or "0"
    events = [{"type": "card_seen", "card_id": first_id, "timestamp_ms": 1000}]
    updated = _call(bridge, "update_progress", progress=progress, feed=feed, events=events)
    assert isinstance(updated, dict)

    checked = _call(bridge, "check_lesson", progress=updated, timestamp_ms=2000, force=True)
    assert isinstance(checked, dict)


def test_material_options_and_planning():
    bridge = make_bridge()
    opts = _call(bridge, "material_options", source_type="pdf")
    assert isinstance(opts, dict)

    pacing = _call(bridge, "pacing", weekly_phone_minutes=120.0, num_units=10,
                   repeats=3, exam_iso="2026-12-01T09:00:00", now_iso="2026-06-30T09:00:00")
    assert isinstance(pacing, dict)

    plan = _call(bridge, "study_plan",
                 exams=[{"name": "Examen", "exam_iso": "2026-12-01T09:00:00", "num_units": 10}],
                 weekly_phone_minutes=120.0, mode="combine", now_iso="2026-06-30T09:00:00")
    assert isinstance(plan, dict)


def test_unknown_method_is_handled_not_raised():
    bridge = make_bridge()
    out = json.loads(bridge.call("does_not_exist", "{}"))
    assert out["ok"] is False
    assert out["error"]["code"] == "unknown_method"
