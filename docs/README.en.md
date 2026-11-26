<div align="center">

<img src="screenshots/tapwing-icon.png" width="96" alt="Tapwing app icon with a stylized wing on a key" />

# Tapwing

**Type, dictate, and edit text privately right from your Android keyboard.**

![Version 2.9.0](https://img.shields.io/badge/Version-2.9.0-6750A4?style=flat-square)
![Android 10+](https://img.shields.io/badge/Android-10%2B-307D5D?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7451BD?style=flat-square&logo=kotlin&logoColor=white)
![License Source Available](https://img.shields.io/badge/License-Source%20Available-405773?style=flat-square)

[Google Play · Closed test](https://play.google.com/apps/testing/com.tapwing.keyboard) · [Screenshots](../README.md#screenshots) · [Deutsch](../README.md)

</div>

Tapwing `2.9.0` (`versionCode = 17`) supports Android 10 and later (`arm64-v8a`), focusing on private on-device dictation, local AI writing tools, and strict data isolation.

## What You Can Do

| Feature | What it does |
| :--- | :--- |
| **Typing & suggestions** | Switch German (`QWERTZ`) / English (`QWERTY`) / `AZERTY` layouts, glide type, accept layout-weighted autocorrections with 1-tap Proactive Undo, and build local per-app vocabulary. |
| **On-device dictation** | Dictate privately with **Sherpa-ONNX** (**NVIDIA FastConformer**, **Parakeet**, **Nemotron**, **Kroko**, **Canary**) or **Whisper**, paired with natural local filler, stutter, punctuation, and bullet-list cleanup. System speech recognition and explicit opt-in cloud APIs are also supported. |
| **Writing aid** | Proofread, shorten, rephrase, or translate using built-in offline rules, optional local GGUF models (auto-unloaded after 120s idle), or opt-in BYO-key cloud providers. |
| **Clipboard & OCR** | Configurable retention (`1h` to `Never`), automatic exclusion of OTP codes & sensitive fields, live CameraX + ML Kit OCR, and single-photo System Photo Picker OCR (**zero storage permissions**). |
| **Dictionaries & snippets** | SHA-256 verified offline `FrequencyWords` packs, Gboard-export compatible `.zip`/`.tsv`/`.txt`/`.json` import & export, plus custom shortcuts (`/date`, `/time`). |
| **Layout & accessibility** | Modular Material 3 settings with search, translucent Glass mode, live height scaling (`75%–135%`), WCAG-AAA high contrast, large key labels, one-handed/split layouts, and Android 11+ inline autofill. |

## Security, Privacy & Licensing Highlights in `2.9.0`
- **CE Storage Isolation**: Personal profile data (`PersonalProfileStorage`, AES-256-GCM encrypted), API keys, clipboard clips, adaptive dictionaries, and typing stats reside strictly in user-unlocked Credential-Encrypted (CE) storage.
- **Opt-In Smart Profile Learning & Sensitive Editor Guard**: Auto-learning defaults to `false`; password, credit card, OTP/PIN, incognito, and `IME_FLAG_NO_PERSONALIZED_LEARNING` editors are always excluded.
- **Zero Storage Permissions**: `READ_MEDIA_IMAGES` and `READ_EXTERNAL_STORAGE` were removed completely.
- **SHA-256 Supply-Chain Verification & Explicit Cloud Consent**: All 17 models and 9 dictionary packs verify exact byte size + SHA-256 before activation, and all cloud features require explicit per-provider opt-in consent with instant revocation in **Settings -> Privacy & Cloud Consent**.
- **Licensing**: Post-`299dc28b80e791e750cff20ff10694b031d6604e` changes are under the [Tapwing Source Available License](../LICENSE), with historical code through `299dc28b80e791e750cff20ff10694b031d6604e` under Apache-2.0. See [docs/licensing.md](licensing.md), [REUSE.toml](../REUSE.toml), and the in-app **Open-Source & Model Licenses** screen.

## Build Locally

Requires **JDK 17 or 21**, **Android SDK 36**, and **Build-Tools 36.0.0** (release ABI targets `arm64-v8a` for compact `Sherpa-ONNX` + `llama.cpp` bundles with verified 16 KB ELF alignment).

```bash
./scripts/check-docs-version.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
./scripts/build-release.sh
```

See [PLAY_STORE_GUIDE.md](../PLAY_STORE_GUIDE.md), [PRIVACY_POLICY.md](../PRIVACY_POLICY.md), and [ARCHITECTURE_ANALYSIS.md](../ARCHITECTURE_ANALYSIS.md) for full details.
