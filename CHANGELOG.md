# Changelog

All notable changes to Tapwing are documented in this file.

## [Unreleased]

### Licensing & Governance
- **Relicensed to `GPL-3.0-or-later`**: Replaced the previous "Tapwing Source Available" license with the GNU General Public License v3.0 or later (`LICENSE`, `LICENSES/GPL-3.0-or-later.txt`), updating all `SPDX-License-Identifier` headers and `REUSE.toml`.
- **GPLv3 Section 7 Exception & Trademarks**: Added `LICENSE-EXCEPTION.md` for linking/combining with Google ML Kit and Google Play services libraries, and `TRADEMARKS.md` clarifying name and logo usage for forks.
- **DCO & Transparency**: Adopted the Developer Certificate of Origin (`Signed-off-by`) in `CONTRIBUTING.md`, updated `docs/licensing.md` (preserving the historical Apache-2.0 baseline through commit `299dc28b80e791e750cff20ff10694b031d6604e`), and added a **Tapwing** entry (`GPL-3.0-or-later`, source link, no-warranty notice) in **Settings -> About / Licenses**.

## [2.9.0] — `versionCode = 17` (2026-11-26)

### Security & Privacy Hardening
- **CE Storage Isolation & Direct Boot Guard**: Moved `PersonalProfileStorage` into user-unlocked Credential-Encrypted (CE) storage encrypted with Android Keystore AES-256-GCM; migrated `AdaptiveDictionaryStore`, `ClipboardStore`, and `TypingStatsStore` files from Device-Protected (DE) storage to CE storage with zero-fill deletion of legacy DE copies and `UserManager.isUserUnlocked()` guards.
- **Opt-In Smart Profile Learning & Sensitive Editor Guard**: Changed `smartProfileAutoLearn` default to `false` (opt-in with explicit confirmation dialog), added `SensitiveEditorGuard` to block profile learning, stats, and clipboard persistence on password, `IME_FLAG_NO_PERSONALIZED_LEARNING`, credit card, and OTP/PIN editors (plus OTP code clipboard filtering), and eliminated cross-editor learning race conditions via `ImeProfileLearningController`.
- **Zero Storage/Media Permissions**: Removed `READ_MEDIA_IMAGES` and `READ_EXTERNAL_STORAGE` completely; kept Photo OCR strictly via Android's single-item `PickVisualMedia` system picker and explicit clipboard image handling.
- **Model & Dictionary Supply-Chain Integrity**: Pinned all 17 downloadable on-device speech/LLM models and 9 FrequencyWords dictionary packs to exact byte sizes and SHA-256 checksums (`DownloadIntegrityVerifier`), enforcing verification before extraction or activation.
- **Explicit Cloud Consent & Loopback-Only HTTP**: Added per-provider `CloudConsentDialog`, active cloud transmission badge in suggestion/voice/writing bars, a dedicated **Privacy & Cloud Consent** screen with instant revocation, and restricted `CloudAiClient` cleartext HTTP strictly to loopback (`localhost`, `127.0.0.1`, `::1`).
- **On-Device Memory & Crash Isolation**: Enforced off-main-thread checks for Sherpa/LLM inference, added a 120s idle auto-unload watchdog for local LLMs, trimmed speech+LLM memory in `onTrimMemory`, and isolated panel actions cleanly.

### Architecture, Licensing & Tooling
- **Modular UI Refactoring**: Split `SettingsScreens.kt` into 13 focused composables and split `InlinePanels.kt` into 6 panel files.
- **Licensing Transparency**: Added `REUSE.toml`, SPDX file headers, updated `docs/licensing.md`, preserved `META-INF/NOTICE*`/`LICENSE*` notices, and added an in-app **Open-Source & Model Licenses** screen (`OpenSourceLicensesCatalog`) with model attribution and clickable license links.
- **Build & CI**: Migrated dependencies to `gradle/libs.versions.toml`, enabled Kotlin/Android lint security rules, added Kover (`0.9.11`) test coverage reporting, GitHub Actions CI + Dependabot, and `scripts/check-docs-version.sh`.

---

## Earlier Releases
- **2.8.0 (`versionCode = 16`)**: Direct-Boot-hardened preferences, `PickVisualMedia` OCR flow hardening, adaptive dictionary & suggestion performance improvements.
- **2.7.0 (`versionCode = 15`)**: On-device GGUF LLM support, multilingual dictation improvements with auto bullet lists, WCAG AAA key contrast & large labels, archive/ZIP hardening.
- **2.6.x and earlier**: Expressive capsule keyboard, customizable Quick Bar, `.zip` personal dictionary import/export, CameraX OCR, Sherpa-ONNX offline speech recognition, Smart Fill.
