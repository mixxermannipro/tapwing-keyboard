# Tapwing Keyboard (`com.tapwing.keyboard`)

**Tapwing** is a clean-room, Apache-2.0 **Material 3 Expressive** Android keyboard built in Kotlin and Jetpack Compose on top of official Android APIs (`InputMethodService`, `SpeechRecognizer`, CameraX + ML Kit Text Recognition, AndroidX DataStore, and hardware-backed Android Keystore).

---

## Highlights

- **Material 3 Expressive UI**: Rounded pill keycaps with top-right number hints, circular modifier and primary action keys, and 4 themes (`Expressive Lavender`, `Material You Dynamic`, `Pixel Blue`, `AMOLED Dark`).
- **German (QWERTZ) & English (QWERTY)**: Built-in umlaut support (`Ä`, `Ö`, `Ü`, `ß`), number row toggle, long-press alternates, spacebar swipe cursor navigation, and double-space period.
- **Natural Voice Dictation**: Uses Android's `SpeechRecognizer` paired with `NaturalVoiceCleaner` to remove filler words (*„ähm“*, *„um“*, *„uh“*), resolve mid-sentence self-corrections (*„nein warte“*, *„wait no“*), apply spoken punctuation, enforce strict language locking, keep dictation alive across 1.5s rotation changes, and offer a minimizable voice toolbar.
- **AI Writing Tools (Offline + Bring-Your-Own-Key)**: Inline *Proofread*, *Professional*, *Casual*, *Concise*, *Emotive*, and *Rephrase* modes. Works out of the box offline via `LocalWritingAssistant`, or connects to your own **Google Gemini** / **OpenAI-compatible** endpoint using an API key encrypted in `AndroidKeyStore`.
- **Extended Clipboard & Screenshot Chip**: Keeps up to 100 unpinned + unlimited pinned clips with configurable retention (`1h`, `24h`, `7d`, `30d`, `Never`) and optional recent-screenshot text extraction.
- **Scan Text (Camera OCR)**: Live CameraX + on-device Google ML Kit text recognition directly from the keyboard toolbar.
- **Proactive Undo & Grammar Chips**: 1-tap Undo chip in the suggestion strip after autocorrect, voice inserts, AI rewrites, or OCR insertions, plus typo/grammar and emoji chips.

---

## Project Structure

```text
Tapwing/
├── app/src/main/java/com/tapwing/keyboard/
│   ├── ai/          # LocalWritingAssistant, CloudAiClient, WritingToolsEngine
│   ├── clipboard/   # ClipboardStore (JSON + retention), ScreenshotMonitor
│   ├── data/        # TapwingPreferences (DataStore), SecureKeyStorage (Keystore AES-GCM)
│   ├── dictionary/  # SuggestionEngine (DE/EN typos, grammar, prefixes, emoji hints)
│   ├── emoji/       # EmojiCatalog
│   ├── ime/         # TapwingInputMethodService, PermissionBridgeActivity
│   ├── ocr/         # ScanTextActivity (CameraX + ML Kit), OcrResultBus
│   ├── settings/    # SettingsActivity & SettingsScreens (incl. ★ Advanced settings)
│   ├── ui/keyboard/ # Expressive KeyboardGrid, SuggestionStrip, VoiceBar & Inline Panels
│   ├── ui/theme/    # Material 3 Expressive theme palettes
│   ├── undo/        # UndoManager & Proactive Undo stack
│   └── voice/       # NaturalVoiceCleaner & TapwingVoiceController
├── app/src/test/    # JUnit unit tests for core engines
├── scripts/         # Automated release build & verification script
├── ARCHITECTURE_ANALYSIS.md
└── PLAY_STORE_GUIDE.md
```

---

## Building Locally (APK & AAB)

Requirements: **JDK 21**, **Android SDK (API 36, Build-Tools 36.0.0)**.

1. Create `local.properties` (excluded from Git):
   ```properties
   sdk.dir=/path/to/Android/sdk
   ```
2. Optional release signing outside the repository (`~/.tapwing-signing/keystore.properties` or env vars `TAPWING_KEYSTORE_PATH`, `TAPWING_STORE_PASSWORD`, `TAPWING_KEY_ALIAS`, `TAPWING_KEY_PASSWORD`).
3. Run tests & build signed APK + AAB:
   ```bash
   ./scripts/build-release.sh
   ```
   Outputs are written to `dist/Tapwing-v1.0.0-release.apk`, `dist/Tapwing-v1.0.0-release.aab`, and `dist/SHA256SUMS.txt`.

---

## Documentation & License

- [ARCHITECTURE_ANALYSIS.md](ARCHITECTURE_ANALYSIS.md) — Clean-room mapping & on-device vs. cloud AI breakdown
- [PLAY_STORE_GUIDE.md](PLAY_STORE_GUIDE.md) — Signing, Data Safety, permissions & Google Play checklist
- [LICENSE](LICENSE) — Apache License 2.0
