# Google Play Store Readiness Guide — Tapwing

Tapwing (`com.tapwing.keyboard`) is a clean-room **Apache-2.0** Android keyboard built on official Android APIs (`InputMethodService`, `SpeechRecognizer`, CameraX + ML Kit Text Recognition, System Photo Picker, AndroidX DataStore, hardware `AndroidKeyStore`) with original Jetpack Compose UI.

---

## 1. Ready-to-Use Play Console Release Package (`v1.2.0`)

Running `./scripts/build-release.sh` produces everything required for Google Play Console in `dist/` (gitignored):

- **Signed Android App Bundle (`.aab`)**: `dist/Tapwing-v1.2.0-release.aab` (upload to *Internal Testing* / *Closed Testing* / *Production*)
- **Signed Release APK (`.apk`)**: `dist/Tapwing-v1.2.0-release.apk` (for direct sideload testing)
- **R8 / ProGuard Deobfuscation Mapping**: `dist/Tapwing-v1.2.0-mapping.txt` (included automatically or uploadable in Play Console for readable crash stack traces)
- **Native Debug Symbols (`SYMBOL_TABLE`)**: embedded inside the `.aab` via `ndk { debugSymbolLevel = "SYMBOL_TABLE" }` in [app/build.gradle.kts](app/build.gradle.kts)
- **16 KB Page-Size Verification**: automatically checked on every 64-bit `.so` (`arm64-v8a`, `x86_64`) inside the `.aab`
- **Checksums**: `dist/SHA256SUMS.txt`
- **Store Graphics & Screenshots**: `dist/play-store-assets/` (`tapwing-icon-512.png`, `tapwing-feature-1024x500.png`, `screenshots/`)

---

## 2. Play Console Documentation Included in Repo

- [PRIVACY_POLICY.md](PRIVACY_POLICY.md) — Bilingual EN/DE privacy policy ready to host on a public HTTPS URL
- [docs/play-store/STORE_LISTING_DE.md](docs/play-store/STORE_LISTING_DE.md) — German store title, short & full descriptions (trademark-clean), release notes, category & tags
- [docs/play-store/STORE_LISTING_EN.md](docs/play-store/STORE_LISTING_EN.md) — English store title, short & full descriptions (trademark-clean), release notes
- [docs/play-store/DATA_SAFETY_AND_REVIEW.md](docs/play-store/DATA_SAFETY_AND_REVIEW.md) — Exact answers for Play Console Data Safety, App Content, Permission Audit (`INTERNET`, `RECORD_AUDIO`, `CAMERA`, `VIBRATE` — zero broad storage permissions!), and reviewer instructions

---

## 3. Signing & Upload Key Setup (`Play App Signing`)

- Release artifacts are signed locally using an external private keystore (`~/.tapwing-signing/tapwing-release.jks`), never committed to Git.
- In Google Play Console:
  1. Enable **Play App Signing** (mandatory for new apps).
  2. Upload `dist/Tapwing-v1.2.0-release.aab` — Google Play registers your local key as the **Upload Key** and signs distribution APKs with Google's managed App Signing Key.
  3. Store encrypted offline backups of `~/.tapwing-signing/tapwing-release.jks` and `keystore.properties` in your password manager.

---

## 4. Operational Steps Remaining in Google Play Console (Browser / Developer Account)

1. Verify `com.tapwing.keyboard` availability in your **Google Play Console** developer account.
2. Host [PRIVACY_POLICY.md](PRIVACY_POLICY.md) at a public HTTPS URL (e.g., GitHub Pages or your own website) and paste it under *App Content > Privacy Policy*.
3. Paste copy from [STORE_LISTING_DE.md](docs/play-store/STORE_LISTING_DE.md) / [STORE_LISTING_EN.md](docs/play-store/STORE_LISTING_EN.md) and upload assets from `dist/play-store-assets/`.
4. Complete *Data Safety*, *Ads*, *Target Audience*, and *Content Rating* using [DATA_SAFETY_AND_REVIEW.md](docs/play-store/DATA_SAFETY_AND_REVIEW.md).
5. Upload `dist/Tapwing-v1.2.0-release.aab` (+ `dist/Tapwing-v1.2.0-mapping.txt`) to the **Internal Testing** track first, run Play Console pre-launch reports, then promote to Closed Testing / Production (note: personal Google Play accounts created after Nov 2023 require the closed test gate before Production).
