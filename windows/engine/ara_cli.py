"""AntiROTAid for Windows — engine host process.

Bridges the native WPF app to the SAME offline Python engine (`engine_ara`) the
Android app uses. Mirrors the Kotlin `EngineBridge` contract exactly: one process
per app run, JSON in / JSON out, everything stays local, no network.

Protocol (newline-delimited JSON over stdin/stdout):
  IN : {"id": <int>, "method": <str>, "args": <object>}
  OUT: {"id": <int>, "envelope": "<the engine's {ok,api_version,data} JSON>"}

The engine itself is unchanged: we import `mindlift.android_bridge.make_bridge`
and forward `bridge.call(method, args_json)`. A blank line / EOF ends the host.
"""
from __future__ import annotations

import json
import os
import sys


def _engine_root() -> str:
    """Locate the vendored engine_ara package.

    Priority: ARA_ENGINE_DIR env var (set by the C# host / installer), then a
    bundled copy next to this script (packaged builds), then the dev tree.
    """
    env = os.environ.get("ARA_ENGINE_DIR")
    if env and os.path.isdir(os.path.join(env, "mindlift")):
        return env
    here = os.path.dirname(os.path.abspath(__file__))
    for cand in (
        os.path.join(here, "engine_ara"),          # packaged: engine/engine_ara/mindlift
        here,                                        # packaged: engine/mindlift
        os.path.join(here, "..", "..", "product", "engine_ara"),  # dev tree
    ):
        cand = os.path.abspath(cand)
        if os.path.isdir(os.path.join(cand, "mindlift")):
            return cand
    return here


def main() -> int:
    # Force UTF-8 on stdio so RO/DE/FR/ES diacritics survive the pipe (Windows
    # consoles default to cp1252 and would mangle them).
    for stream in (sys.stdin, sys.stdout):
        try:
            stream.reconfigure(encoding="utf-8", newline="\n")
        except Exception:
            pass
    sys.path.insert(0, _engine_root())
    try:
        from mindlift.android_bridge import make_bridge
    except Exception as exc:  # engine missing/broken — report once and exit
        sys.stdout.write(json.dumps({
            "id": 0,
            "envelope": json.dumps({
                "ok": False, "api_version": "1",
                "error": {"code": "engine_missing",
                          "message": f"{type(exc).__name__}: {exc}",
                          "recoverable": False},
            }),
        }) + "\n")
        sys.stdout.flush()
        return 1

    bridge = make_bridge()
    # Signal readiness so the C# host knows the engine imported cleanly.
    sys.stdout.write(json.dumps({"id": 0, "ready": True}) + "\n")
    sys.stdout.flush()

    for line in sys.stdin:
        line = line.lstrip("﻿").strip()  # tolerate a stray UTF-8 BOM
        if not line:
            break
        try:
            req = json.loads(line)
            rid = req.get("id", 0)
            method = req.get("method", "")
            args = req.get("args", {})
            envelope = bridge.call(method, json.dumps(args, ensure_ascii=False))
        except Exception as exc:
            rid = 0
            envelope = json.dumps({
                "ok": False, "api_version": "1",
                "error": {"code": "host_error",
                          "message": f"{type(exc).__name__}: {exc}",
                          "recoverable": True},
            })
        sys.stdout.write(json.dumps({"id": rid, "envelope": envelope},
                                    ensure_ascii=False) + "\n")
        sys.stdout.flush()

    bridge.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
