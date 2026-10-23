# Third-party software and credits

AntiROTAid is built on the open-source components below. Their copyrights stay
with their authors; each is used under its own license.

## Android app (`product/android`)

| Component | Version | License |
|---|---|---|
| Kotlin | 2.0.20 | Apache License 2.0 |
| Android Gradle Plugin | 8.5.2 | Apache License 2.0 |
| AndroidX Core KTX | 1.13.1 | Apache License 2.0 |
| AndroidX Lifecycle | 2.8.6 | Apache License 2.0 |
| AndroidX Activity Compose | 1.9.2 | Apache License 2.0 |
| Jetpack Compose (BOM) incl. Material 3 and Material Icons | 2024.09.03 | Apache License 2.0 |
| AndroidX Navigation Compose | 2.8.1 | Apache License 2.0 |
| Chaquopy (Python for Android) | 17.0.0 | MIT License |
| Python | 3.11 (bundled by Chaquopy) | Python Software Foundation License |
| JUnit (tests only) | 4.13.2 | Eclipse Public License 1.0 |
| org.json (tests only) | 20240303 | Public domain |

## Card engine (`product/engine_ara`)

| Component | Version | License |
|---|---|---|
| pypdf, vendored in `product/engine_ara/pypdf` | 6.13.3 | BSD 3-Clause, see `product/engine_ara/pypdf/LICENSE` |

The rest of the engine uses only the Python standard library.

## Windows app (`windows`)

| Component | Version | License |
|---|---|---|
| .NET 8 / WPF | 8.0 | MIT License |
| Python embeddable package (bundled in the Store build, not in this repository) | 3.12 | Python Software Foundation License |
| Roboto font, `windows/AntiROTAid.Windows/Assets/fonts` | 2.001047 | Apache License 2.0, © 2015 Google Inc. |

## Development help

Parts of the code were written or revised with AI coding assistants (OpenAI
ChatGPT and Anthropic Claude). The README, section *How it was built*, says
how they were used.
