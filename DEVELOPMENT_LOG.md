# Development log

This is the commit history of the private working repository in which AntiROTAid was built, copied here verbatim (date, short commit id, message). The private repository is not published because its history contains build secrets; this public repository starts from a clean snapshot of release 1.0.7.

Commit messages are the original ones and are left unedited, including references to the AI assistants used during development (see the README, section *How it was built*).

Total: 91 commits, 2026-05-25 → 2026-10-10.

## Phase 1 — Card engine and shared codebase (May 25 – Jun 13, 2026)

The offline Python engine that turns course material into study cards, plus features for MindLift, the working name of a planned broader version of the same idea for other areas beyond studying. Those MindLift features are not part of AntiROTAid or of this repository.

| Date | Commit | Message |
|---|---|---|
| 2026-05-25 | `ad511b5` | Initialize repository safety rules |
| 2026-05-25 | `253ff22` | Define AI worker responsibilities |
| 2026-05-25 | `135458d` | Define MindLift construction plan |
| 2026-05-25 | `3162dd4` | Define MindLift product strategy |
| 2026-05-25 | `fa7ea2c` | Create initial product skeleton |
| 2026-05-25 | `0d80769` | Add baseline product test harness |
| 2026-05-25 | `3f1b266` | Add foundation engines pack |
| 2026-05-25 | `bbdef25` | Add core loop engine pack |
| 2026-05-26 | `d968ba4` | Add recovery intelligence and demo layers |
| 2026-05-26 | `d5d26e5` | Import MindLift final base |
| 2026-06-06 | `170164e` | Adaptive concept+relation card generator + aggressive doomscroll cadence |
| 2026-06-11 | `3c357d7` | Life modes v1: bills/meetings/bedtime join the war on doomscrolling |
| 2026-06-11 | `26618e6` | Discipline pack: evening contract, bypass escalation, sleep debt |
| 2026-06-11 | `026fc56` | Patterns pack: personal binge profile, first scroll, streak, receipt, vacation, preemptive nudge |
| 2026-06-11 | `b64ba13` | War report: the honest weekly standing, display-only |
| 2026-06-11 | `0391811` | Scroll tax + people-days: cards buy the scroll, birthdays interrupt it |
| 2026-06-11 | `417f288` | Panic mode, charging ritual, progressive friction, streak medals |
| 2026-06-11 | `683f623` | Monitor tick consolidation, redemption, anti-habituation messages |
| 2026-06-11 | `a1b923c` | Sacred study hour, weekly streak shield, Sunday check-in |
| 2026-06-11 | `8e9e8ea` | Scroll economy, multi-exam war, ranks, postmortem, PC sentinel |
| 2026-06-11 | `38528b2` | Review fixes: wallet at the source, derived exam signals, fair dismissal, anti-grind |
| 2026-06-11 | `d0f18dd` | Build hygiene: auto-mirror engine_runtime each build; drop dead JS prototype |
| 2026-06-12 | `35105dc` | Per-lecture deck generation + vacuous-answer gate |
| 2026-06-12 | `77203cb` | Oral exam points: defend every generated keyword |
| 2026-06-13 | `0e9f874` | Commit the buyer-review fixes that were stranded in the working tree |
| 2026-06-13 | `f9a7bd9` | Concept filter: reject verb/participle fragments and bare adjectives (review #4) |
| 2026-06-13 | `9966f83` | Bad-material detector: refuse vague/logistical notes before card generation (review #4 P4) |
| 2026-06-13 | `2ae75f2` | Domain-aware extraction + card-template variety (review #4 P3 & P5) |

## Phase 2 — AntiROTAid takes shape (Jun 16 – Jun 27, 2026)

The AntiROTAid app itself: your own materials, as short cards, shown over other apps only when you turn the mode on yourself. A lean engine (`engine_ara`) and a separate Android flavor (`ara`) were created, then iterated on real-device testing.

| Date | Commit | Message |
|---|---|---|
| 2026-06-16 | `7f73759` | Engine: commit stranded conceptgen page-feed + MEGA F1-F8 + opt-in Gemini connector |
| 2026-06-16 | `801f60e` | Security: git-ignore signing keystores (*.jks/*.keystore) |
| 2026-06-16 | `beef985` | Partner-grade Gemini connector + end-to-end demo + Google partnership brief |
| 2026-06-16 | `1b41022` | MEGA F9 focus-session planner + F12 mega_dashboard (one-call home screen) |
| 2026-06-16 | `b2123db` | MEGA F10 evening reflection (shutdown ritual) |
| 2026-06-16 | `57bcc48` | MEGA F13 life_dashboard (adult/employee home screen) |
| 2026-06-16 | `d965286` | Demo: add F13 adult-mode home screen (bills + meetings + sleep) |
| 2026-06-16 | `b26a3ab` | Add interactive engine console + Google onepager + demo smoke tests |
| 2026-06-16 | `d8e6ad5` | Add smart_explain — Gemini-backed concept explanation (offline fallback) |
| 2026-06-16 | `9a2f74e` | Add HTML pitch generator (make_pitch.py) |
| 2026-06-16 | `c1a0ef5` | Pitch page: embed a live engine transcript (self-proving) |
| 2026-06-16 | `479f1ad` | Add verify_local.py — one-command local product self-check |
| 2026-06-16 | `fb62c90` | Chore: git-ignore local scratch folder (work/) |
| 2026-06-16 | `f312f9d` | AntiROTAid lean engine (engine_ara) from ChatGPT finish/polish pack |
| 2026-06-16 | `24dbc71` | AntiROTAid card-feed UI (ara flavor) on the lean engine |
| 2026-06-16 | `435a83e` | AntiROTAid ara v1 UX overhaul (my version, pre-ChatGPT optimization) |
| 2026-06-16 | `edff147` | ChatGPT BETON UI passes applied + made buildable |
| 2026-06-16 | `9368043` | Fix regressions in ChatGPT's UI passes (device-verified) |
| 2026-06-16 | `7248ccf` | Fix deadline/progress not updating live (data-class recomposition skip) |
| 2026-06-16 | `4c19eb4` | Fix images/import broken in ara release: disable R8 minify+shrink |
| 2026-06-21 | `4bb1e9a` | ara: diagram-card fix + UI/import polish, vendored pypdf, demo lessons |
| 2026-06-21 | `00a8343` | ara: slide-deck PDF cards show math via slide image, drop lossy caption |
| 2026-06-21 | `0631ea8` | ara: original demo lessons + scrub course-specific fingerprints |
| 2026-06-21 | `54c830c` | ara: replace home preview cards with original demo content |
| 2026-06-21 | `17775ac` | ara engine: math/LaTeX text normalizer + page-sentence reconstruction |
| 2026-06-21 | `865733d` | ara engine: stop math sub/superscript rules eating next-line enumerators |
| 2026-06-21 | `8410e24` | ara bridge: show normalized PDF text, not image-only, for slide-deck decks |
| 2026-06-21 | `71bed67` | ara engine: study planner (pacing + per-material options + multi-exam scheduler) |
| 2026-06-21 | `9602955` | ara engine: per-material card size (small/medium/large) splits units finer |
| 2026-06-21 | `99a36dc` | ara: add Exam->Materials data model (additive, no UI change yet) |
| 2026-06-21 | `7aebb95` | ara UI: per-material card settings sheet + study-plan pacing in Settings |
| 2026-06-21 | `4e8b781` | ara overlay: surface ONE logical unit per appearance, not the whole deck |
| 2026-06-21 | `18594b2` | ara UI: Examenele mele screen (exam -> materials + auto study plan) |
| 2026-06-24 | `fad97b3` | @ ara engine: 2-axis card model (MOD x GRANULARITATE) with logical-unit + concept splitting |
| 2026-06-24 | `7d2befe` | @ ara UI: per-material MOD + GRANULARITATE selectors with 5-language instructaj |
| 2026-06-24 | `4e026eb` | @ ara capture mode: fix over-splitting, captions, count consistency |
| 2026-06-24 | `9a965c3` | @ ara capture: skip academic-integrity / course-policy slides |
| 2026-06-24 | `2f86dfa` | @ ara UI: delete individual cards from the study feed |
| 2026-06-24 | `e5d3236` | @ ara engine: DOCX paragraphs, ZIP-of-images, import report, robustness |
| 2026-06-24 | `df27ce1` | @ ara UI: PDF AS-IS scrollable page + per-card progress (seen/completed/scroll) |
| 2026-06-24 | `f9cecbf` | ara UI: library shows "Parcurs: Nx" + resume hint per material |
| 2026-06-24 | `ed0896a` | ara: import modes are per file-type SplitMode (spec), drop the 3-mode/granularity model |
| 2026-06-24 | `1f2cfe7` | ara UI: per file-type mode picker (PDF only), no granularity, split-mode labels |
| 2026-06-25 | `57fe593` | ara UI: add missing AraCoach live walkthrough component |
| 2026-06-25 | `ea68bf0` | ara: localize import-error messages and mode-active notification |
| 2026-06-25 | `8c73056` | ara: pace overlay cards from the study-plan engine, not a fixed interval |
| 2026-06-25 | `56f34eb` | ara engine: fall back to slide's raw text when no embeddable image exists |
| 2026-06-25 | `6ad0b36` | ara: bump to vc33 / 0.6.0-offline |
| 2026-06-25 | `ec9cd31` | ara: honesty disclaimer on consent/info + nag messages on overlay cards |
| 2026-06-25 | `2208dc1` | ara: handle system/gesture back as up-navigation, not app exit |
| 2026-06-26 | `bc1b062` | ara: home declutter, course/exam links, sorting, opt-in auto-delete |
| 2026-06-27 | `e653c47` | ara: whole-course scrollable feed with resume + per-card next/stay CTAs |
| 2026-06-27 | `1402df3` | ara: drop standalone Exit pill from card toolbar; merge exit into "stay here" |
| 2026-06-27 | `37311db` | ara: dark container for exam material-picker dialogs so names are readable |
| 2026-06-27 | `2635e52` | ara: make dialog containers theme-aware (fixes light themes) |
| 2026-06-27 | `bc94749` | ara: set text colors on the exam-name input so the typed name is visible |

## Phase 3 — Release engineering (Aug 12, 2026)

The published Google Play build (1.0.5) was preserved as an exact baseline, then updated to target Android 16 / API 36 (1.0.6).

| Date | Commit | Message |
|---|---|---|
| 2026-08-12 | `0d65882` | chore(release): preserve published AntiROTAid 1.0.5 vc6 baseline |
| 2026-08-12 | `fb143c8` | chore(android): preserve LF for production source files |
| 2026-08-12 | `05f7235` | fix(gitattributes): keep engine_ara mirror byte-stable across builds |
| 2026-08-12 | `b8d87d5` | build(android): target Android 16 / API 36, release 1.0.6 (vc7) |
| 2026-08-12 | `6226abd` | build(chaquopy): ship the engine as .py source, not .pyc |

## Phase 4 — Bug fix from real use (Oct 10, 2026)

Text slides from TXT/notes files were wrongly shown as "visual slide — no extractable text". Fixed in 1.0.7.

| Date | Commit | Message |
|---|---|---|
| 2026-10-10 | `7f4a570` | fix(cardifier): keep text slides from TXT/notes as text, not "visual slide" |
| 2026-10-10 | `bd1d728` | build(ara): release 1.0.7 (vc8) with the TXT-slide card fix |

