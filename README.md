# AntiROTAid

**An offline study app that turns your own course materials into short cards
and, only when you switch it on, shows them over the other apps on your
phone.**

Made by **Teodor-Mihai Stupariu**, a high-school student, as a one-person
project with no funding.

- Website: <https://antirotaid.netlify.app/>
- Google Play (Android): <https://play.google.com/store/apps/details?id=app.antirotaid>
- Microsoft Store (Windows): <https://apps.microsoft.com/detail/9NL967H7JLVN>
- Contact: antirotaid@gmail.com

## What it does

1. **Import your materials.** PDF, slides, Word documents, text files or
   images. A PDF can be split by page or by paragraph.
2. **Get cards.** An on-device engine splits each material into short,
   readable study cards (text, or the slide image when the slide is mostly a
   diagram or formula).
3. **Plan around exams.** You add exams, attach materials and say how much
   phone time you have. The app works out a study rhythm.
4. **AntiROTAid Mode.** You start it yourself. While it is on, a card appears
   over whatever is on screen at the pace set by your exam plan. Every card
   can be closed, and the mode stops from its notification.

## Privacy by construction

The Android app declares no `INTERNET` permission, so it cannot open a network
connection: imported materials cannot leave the phone. It uses no usage-stats
access and no accessibility service, so it does not know which app you are in.
The overlay is triggered only by your own timer.

Permissions the app declares, all in
[`product/android/app/src/ara/AndroidManifest.xml`](product/android/app/src/ara/AndroidManifest.xml):

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw a study card over other apps while the mode is on |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the mode running visibly |
| `POST_NOTIFICATIONS` | The notification with the Stop button |

The build tools also add `app.antirotaid.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`,
a signature-level permission private to the app. The copy distributed by
Google Play also lists `com.android.vending.CHECK_LICENSE`, which is not in
this source code.

## Status

| Part | Status |
|---|---|
| Android app 1.0.7 (versionCode 8) | Source in this repository. Builds and passes its unit tests. |
| Android app on Google Play | Published |
| Windows app 1.0.0 | Published on the Microsoft Store. Source in `windows/`. |
| Card engine | 24 automated tests pass |
| Known issues, in progress | A PDF title that spans two lines can be split across a card heading and its first line. After switching an exam to a fixed number of minutes between cards, the exam's "Plan" line can still show the previous pace. |

## Repository layout

```
product/
  android/          Android app (Kotlin, Jetpack Compose); the published app is the `ara` flavor
  engine_ara/       Card engine (Python, standard library + vendored pypdf), with tests
windows/            Windows app (C#, WPF, .NET 8) that reuses the same Python engine
DEVELOPMENT_LOG.md  Full commit history of the private working repository
THIRD_PARTY_NOTICES.md
```

### How the pieces fit

The Android UI is written in Kotlin with Jetpack Compose. The app embeds a
Python interpreter through Chaquopy and calls the card engine through a JSON
bridge (`EngineBridge.kt` ↔ `mindlift/android_bridge.py`). The Windows app
talks to the same engine through a small host process
(`windows/engine/ara_cli.py`) using the same JSON contract. Card generation
therefore behaves the same on both platforms.

The overlay is drawn by `OverlayWindowService`. `AraModeService` runs it as a
foreground service and spaces the cards from the exam plan.

Some package and class names say `mindlift` (for example `com.mindlift`).
MindLift is the working name of a planned, broader version of the same idea
for other areas beyond studying. The codebase is shared, so
`product/android/app/src/main` also contains code for that version which
AntiROTAid does not use (an app-usage monitor and related screens). The
published app's manifest declares none of the components or permissions that
code would need.

## Build and run

### Android

Requirements: Android Studio (or JDK 17) and the Android SDK, API 36.

```powershell
cd product/android
./gradlew :app:assembleAraDebug          # debug APK, no signing key needed
./gradlew :app:testAraDebugUnitTest      # unit tests
```

The APK is written to `product/android/app/build/outputs/apk/ara/debug/`.
Release builds need the upload key, which is not part of this repository; see
[`product/android/RELEASE_SIGNING.md`](product/android/RELEASE_SIGNING.md).
The release build is arm64-only. To run on an x86_64 emulator, add `x86_64` to
`abiFilters` in `product/android/app/build.gradle.kts`.

### Card engine tests

```powershell
cd product/engine_ara
python -m pytest -q
```

### Windows

Requirements: Windows 10/11, .NET 8 SDK, Python 3.11+. See
[`windows/README.md`](windows/README.md).

```powershell
cd windows/AntiROTAid.Windows
dotnet run -c Debug
```

## How it was built

**My role: product and user experience.** I decided what problem the app
solves, how it should look and how it should behave. While designing, I put
myself in the place of the student using it: what they see first, what they
tap, when a card should appear and how they get rid of it. I described those
flows and screens in detail, tried every build on a phone, and asked for
changes until the app behaved the way I had in mind.

**The code was written with AI coding assistants.** Most of it was written by
Claude Code (Anthropic) from my descriptions. Some UI passes in June 2026 came
from ChatGPT (OpenAI). The commit messages in `DEVELOPMENT_LOG.md` show where.
I did not write most of the code line by line. My work was the design, the
direction, the testing and the decisions.

**Testing.**
1. On my own phone, after every build.
2. On my family's phones, to see how people who had not built the app use it.
3. Through Google Play closed testing, before the public release.

Automated tests also cover the card engine (24 tests) and the Android app's
resume and cleanup logic (17 tests).

**Releases.** I published the app on Google Play (Android) and the Microsoft
Store (Windows) and kept it updated through several releases.

## Credits

Open-source components and their licenses are listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Copyright

© 2026 Teodor-Mihai Stupariu. All rights reserved. The source code is
published so it can be read and reviewed. No license to reuse it is granted.
Third-party components keep their own licenses.
