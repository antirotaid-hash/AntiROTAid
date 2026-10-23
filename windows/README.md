# AntiROTAid for Windows

## Deliverables (in `dist\` after building)
- **`dist\AntiROTAid\`** — the standalone app. Runs on any Windows 10/11 with
  **nothing pre-installed** (bundles the .NET runtime + Python + the engine).
  Double-click `AntiROTAid.exe`.
- **`dist\AntiROTAid-Windows-v0.1.0.zip`** — the same, zipped for handoff (~78 MB).
- **`dist\AntiROTAid.msix`** — Store package. Set your Partner Center identity in
  `msix\AppxManifest.xml`, rebuild with `build-msix.ps1`, then upload/sign.
- **`store-screenshots\`** — screenshots for the listing.

Rebuild everything: `package.ps1` (standalone) then `build-msix.ps1` (Store).

---

Native Windows port of the Android AntiROTAid / ARA app. Same identity, same
offline study engine, same mechanic: **you turn on AntiROTAid Mode and a study
card floats over your screen on a timer.** No account, no tracking, no network,
and it does **not** read which app you're using (impossible here and unwanted).

This project is fully separate from the Android app under `product/android` and
does not touch it, its build, or its keystore.

## What's reused from Android (not reinvented)
- **Engine**: the exact `product/engine_ara` Python package (offline card
  generation from your material), called via a small host process — no rewrite.
- **Palette / themes**: all 5 ARA themes (Dark Gold, Amoled Black, Light Clean,
  Neon Focus, Deep Blue), ported 1:1 from `AraBrand` → `Themes/AraTheme.cs`.
- **Assets**: the real `ara_wordmark.png` logo and launcher icon, copied from
  `src/ara/res`.
- **Mechanic**: `AraModeService.cardLoop` → `ModeController` (first card ~4s
  after Start, then every N minutes; dismissible; Stop any time).

## Requirements
- Windows 10/11
- **.NET 8 SDK** (Windows Desktop) — `winget install Microsoft.DotNet.SDK.8`
- **Python 3.11+** on the machine (runs the offline engine). A bundled
  interpreter can be pinned later via the `ARA_PYTHON` env var.

## Build & run (dev)
```powershell
cd windows\AntiROTAid.Windows
dotnet run -c Debug
```
Or build a Release binary:
```powershell
dotnet publish -c Release -r win-x64 --self-contained false -o ..\dist
..\dist\AntiROTAid.exe
```

The app finds the engine automatically (dev tree or a copy bundled next to the
exe). To pin paths explicitly:
- `ARA_PYTHON`   → full path to `python.exe`
- `ARA_ENGINE_DIR` → folder containing the `mindlift` package (i.e. `engine_ara`)

## Layout
```
windows/
  engine/ara_cli.py            # engine host: JSON in/out over stdio, offline
  AntiROTAid.Windows/
    App.xaml(.cs)              # startup: theme, engine, controller, tray
    MainWindow.xaml(.cs)       # ARA home: status, Start/Stop, import, settings
    Overlay/OverlayWindow.*    # the always-on-top study card
    ModeController.cs          # the timed card mechanic
    Engine/EngineBridge.cs     # talks to ara_cli.py
    Engine/AraCard.cs          # card model + feed parser (mirrors Android)
    Themes/AraTheme.cs         # 5 brand themes
    TrayIcon.cs                # tray: Start/Stop/Show/Exit
    Settings.cs                # local prefs (%AppData%\AntiROTAid)
    Assets/                    # ara_wordmark.png, ara_icon.png
```

## Packaging for Microsoft Store (later)
1. Bundle a Python runtime + `engine_ara` next to the exe (or embed via
   `python-embed`), so the machine doesn't need Python installed.
2. `dotnet publish -c Release -r win-x64` → wrap the output in an **MSIX**
   package (Windows Application Packaging Project or `MakeAppx`).
3. Because the app declares **no** special capabilities (no usage access, no
   background app-scanning, no network), it fits the Store sandbox cleanly.
4. Sign, then submit via Partner Center.

## Privacy notes (for the Store listing)
AntiROTAid for Windows works entirely on your device. It has no account, no
analytics, no network calls, and never reads which apps or windows you use or
your running processes. Your imported study material and progress stay in your
local user folder.
