# Google Play Store Readiness Guide — Tapwing

Tapwing is built clean-room under **Apache-2.0** with its own `applicationId` (`com.tapwing.keyboard`) and original Jetpack Compose codebase — making it structurally compatible with Google Play distribution once the operational release requirements below are completed.

---

## 1. Signing & Upload Key Setup (`Play App Signing`)
- **Do not distribute debug keys.** Locally, release artifacts are signed with a private keystore stored outside Git (`~/.tapwing-signing/tapwing-release.jks`).
- When enrolling in **Google Play Console**:
  1. Enable **Play App Signing** (required for new apps).
  2. Upload the generated `.aab` (`dist/Tapwing-v1.0.0-release.aab`) — Play Console registers your local release key as the **Upload Key** and generates/manages the final **App Signing Key** safely inside Google's infrastructure.
  3. **Back up** your local keystore (`tapwing-release.jks`) and credentials in a password manager/offline encrypted backup; losing your upload key requires a Play Console key reset request.

## 2. Package Name & Store Listing Identity
- Current `applicationId`: `com.tapwing.keyboard`.
- Verify availability of `com.tapwing.keyboard` in your Google Play Developer Console before creating the app listing (application IDs must be globally unique on Google Play and cannot be changed after the first APK/AAB upload).
- **Trademark & Listing Hygiene:**
  - Keep store title, icon, screenshots, and description strictly branded as **Tapwing**.
  - Do **not** imply official affiliation with Google LLC, Gboard, Pixel, or Gemini, and do not copy Google brand icons.

## 3. Permissions & Google Play Data Safety Declarations

| Permission | Purpose in Tapwing | Required Play Console Action |
| :--- | :--- | :--- |
| `BIND_INPUT_METHOD` | Standard Android IME service declaration | Declare prominent in-app privacy notice (built into *About* & onboarding sheets); clarify keystrokes stay on-device unless user explicitly triggers optional cloud AI. |
| `RECORD_AUDIO` | Voice typing via Android `SpeechRecognizer` | Runtime prompt only when user triggers microphone; declare optional audio handling in Data Safety. |
| `CAMERA` | Scan Text (Camera OCR) via CameraX + ML Kit | Runtime prompt only when user opens *Scan Text*; processing happens on-device via ML Kit. |
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` (<=32) | Optional recent screenshot chip OCR | Runtime opt-in via Settings; consider switching to Android Photo Picker if Play policy scrutinizes broad `READ_MEDIA_IMAGES` usage for a keyboard app. |
| `INTERNET` | Optional user-configured Gemini / OpenAI API requests | Clarify in Privacy Policy & Data Safety that no telemetry is collected and network calls occur only when user activates a cloud AI provider with their own key. |

## 4. Required Pre-Submission Checklist
- [ ] Publish a public **Privacy Policy URL** covering on-device typing, local clipboard retention, voice recognition delegation, ML Kit camera OCR, and optional bring-your-own-key AI requests.
- [ ] Complete the **Data Safety** form and **App Content / Ads / Target Audience** questionnaires in Play Console.
- [ ] Review `READ_MEDIA_IMAGES` policy justification (or restrict screenshot-OCR to manual Photo Picker selection) before production track review to avoid Photo & Video Permissions policy rejection.
- [ ] Test the signed `.aab` via `bundletool` or Google Play **Internal Testing** track before opening Production rollout.
