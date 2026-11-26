# Tapwing Google Play Store Guide

Tapwing (`com.tapwing.keyboard`) is an Android keyboard built on official Android APIs (`InputMethodService`, `SpeechRecognizer`, `CameraX` + `ML Kit`, `PickVisualMedia`, `AndroidX DataStore`, `AndroidKeyStore` AES-256-GCM) with a Jetpack Compose UI. See [docs/licensing.md](docs/licensing.md) and [LICENSE](LICENSE) for **Tapwing Source Available** licensing terms (with Apache-2.0 baseline through commit `299dc28b80e791e750cff20ff10694b031d6604e`).

---

## 1. Ready-to-Use Play Console Release Package (`v2.9.0`, `versionCode = 17`)

Run `./scripts/build-release.sh` locally to verify docs, unit tests, 16 KB ELF page alignment, ABI size budgets, and generate the signed artifacts outside Git (`dist/`, gitignored):
- **Signed Android App Bundle (`.aab`)**: `dist/Tapwing-v2.9.0-release.aab` (upload to *Internal Testing* / *Closed Testing* / *Production*)
- **Signed Release APK (`.apk`)**: `dist/Tapwing-v2.9.0-release.apk` (for direct sideload testing)
- **R8 / ProGuard Deobfuscation Mapping**: `dist/Tapwing-v2.9.0-mapping.txt`
- **Target ABI & 16 KB Page Alignment**: `arm64-v8a` only (keeps native Sherpa-ONNX + llama.cpp binaries compact within Play limits), verified `PT_LOAD >= 16384`.

---

## 2. Play App Signing Setup

- Signing credentials live outside the repository at `~/.tapwing-signing/keystore.properties` and `~/.tapwing-signing/tapwing-release.jks`.
- Do **not** commit keystores, `keystore.properties`, or API keys to Git.

---

## 3. Store Console URLs & Submission Steps

- **Hosted Privacy Policy URL (Cloudflare Workers):** `https://tapwing-keyboard.mixxermannipropvp.workers.dev/privacy`
- **Website / Support URL:** `https://tapwing-keyboard.mixxermannipropvp.workers.dev`
- **Pre-written Listing Texts (`de-DE` / `en-US`):** [docs/play-store/STORE_LISTING_DE.md](docs/play-store/STORE_LISTING_DE.md) and [docs/play-store/STORE_LISTING_EN.md](docs/play-store/STORE_LISTING_EN.md)
- **Data Safety & Permission Declarations:** [docs/play-store/DATA_SAFETY_AND_REVIEW.md](docs/play-store/DATA_SAFETY_AND_REVIEW.md)

### Upload Flow
1. Run `./scripts/build-release.sh` cleanly (`0` errors, tests & docs checks green).
2. Confirm *App Content > Privacy Policy* points to `https://tapwing-keyboard.mixxermannipropvp.workers.dev/privacy` and *Data Safety* matches [DATA_SAFETY_AND_REVIEW.md](docs/play-store/DATA_SAFETY_AND_REVIEW.md) (no storage/media permissions, opt-in cloud consent).
3. In Google Play Console (`com.tapwing.keyboard`), open your target track (*Production* / *Closed Testing*), click **Create new release**, upload `dist/Tapwing-v2.9.0-release.aab`, copy release notes (`<= 500` chars) for `de-DE` and `en-US`, review, and roll out.
