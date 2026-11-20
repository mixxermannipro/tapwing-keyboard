# Tapwing Keyboard (`com.tapwing.keyboard`)

**Tapwing** is a clean-room, Apache-2.0 **Material 3 Expressive + Apple Liquid Glass** Android keyboard built in Kotlin and Jetpack Compose on top of official Android APIs (`InputMethodService`, `SpeechRecognizer`, CameraX + ML Kit Text Recognition, System Photo Picker, AndroidX DataStore, and hardware-backed `AndroidKeyStore`).

---

## Highlights (`v1.2.0`)

- **1:1 Pixel Slate & Material 3 Expressive Capsule UI**: Full stadium-pill keycaps with centered top symbol hints (`% \ | = [ ] < > { }`, `@ # € _ & - + ( )`, `* " ' : ; ! ?`), dedicated `1–0` number row, clean unmarked spacebar, 5 themes (`Pixel Slate`, `Expressive Lavender`, `Material You Dynamic`, `Pixel Blue`, `AMOLED Dark`), and a borderless bottom-right dismiss chevron (`⌄`).
- **Apple Mode (Liquid Glass)**: Toggleable translucent glass keycaps with vertical specular highlights, spring keycap animations, and floating preview bubbles — while preserving the exact same 1:1 key layout.
- **Minimalist Apple-Style Settings & Back-Swipe Navigation**: Calm inset-grouped iOS-style Settings with single-line rows, hairline dividers, right-aligned checkmarks, borderless search/input pills, and `BackHandler`-driven predictive back-swipe navigation across sub-screens.
- **Customizable & Reorderable Quick-Actions Strip**: Choose and reorder up to 8 shortcuts above the keyboard (`QuickBarAction`), either directly inline via long-press / *Tools Hub > Edit bar* (`QuickBarCustomizePanel`) with horizontal drag-swap and move controls, or in *Settings > Quick Bar* (`QuickBarSettingsScreen`).
- **Gboard/PixelBoard `.zip` Dictionary Import/Export & Adaptive Learning**: 1:1 `.zip` (`dictionary.txt` with `# Gboard Dictionary version:1` / `version:2`), `.txt`, `.csv`/`.tsv`, `key => phrase`, and `.json` import plus `.zip` & `.json` export. Automatically learns unigrams, next-word bigrams, shortcuts (`/datum`, `/date`, `/zeit`, `/time`), and rejected autocorrections on-device.
- **Dynamic Live Resizing & Power Tools**: Smooth continuous height slider (`75%–135%`) + live interactive drag-border resizer, bottom offset control, **Cursor & Text Edit D-Pad** (`← ↑ ↓ →`, `Select`, `Copy/Cut/Paste`), **One-Handed Mode** (Left/Right), **Inline Math** (`24*7=` → `168`), **Swipe-Backspace Word Delete**, **In-Keyboard Quick Search**, and **Incognito Mode**.
- **Dictation**: Use Android offline for live text when your device has a supported language pack, or download Whisper to process audio on the device. For optional cloud dictation, save your own Gemini or OpenAI API key and select Cloud in Dictation settings. Gemini can transcribe and clean up audio in one request.
- **Paste suggestions**: Tap the copied text above the keyboard to paste it. Tapwing hides suggestions in private fields and ignores clips marked sensitive by Android. Copied verification codes get a short-lived one-tap suggestion without entering clipboard history.
- **Autofill**: Android 11+ can show suggestions from the configured provider directly in the keyboard. For Bitwarden, select it as Android’s autofill provider and enable Inline in its autofill settings.
- **AI Writing Tools (Offline + Bring-Your-Own-Key)**: Inline *Proofread*, *Professional*, *Casual*, *Concise*, *Emotive*, and *Rephrase* modes via offline rules (`LocalWritingAssistant`) or optional **Google Gemini** / **OpenAI-compatible** API key encrypted in `AndroidKeyStore`.
- **Privacy-First OCR & Extended Clipboard**: Keeps up to 200 (default 100) unpinned + pinned clips with configurable retention (`1h` to `Never`), live CameraX + ML Kit OCR, and Android System Photo Picker single-image OCR (zero gallery storage permissions required).

---

## Project Structure

```text
Tapwing/
├── app/src/main/java/com/tapwing/keyboard/
│   ├── ai/          # LocalWritingAssistant, CloudAiClient, WritingToolsEngine
│   ├── clipboard/   # ClipboardStore, ScreenshotMonitor (Photo Picker OCR helper)
│   ├── data/        # QuickBarAction, TapwingPreferences (DataStore), SecureKeyStorage
│   ├── dictionary/  # CustomDictionaryParser (.zip/tsv/json), AdaptiveDictionaryStore, SuggestionEngine
│   ├── emoji/       # EmojiCatalog
│   ├── ime/         # TapwingInputMethodService, PermissionBridgeActivity
│   ├── ocr/         # ScanTextActivity (CameraX + ML Kit + PickVisualMedia), OcrResultBus
│   ├── settings/    # SettingsActivity, AppleSettingsComponents, SettingsScreens
│   ├── ui/keyboard/ # KeyboardGrid, SuggestionStrip, QuickBarCustomizePanel & Inline Panels
│   ├── ui/theme/    # Material 3 Expressive & Apple Liquid Glass palettes
│   ├── undo/        # UndoManager & Proactive Undo stack
│   └── voice/       # NaturalVoiceCleaner & TapwingVoiceController
├── app/src/test/    # JUnit unit tests (23 tests across all core engines)
├── docs/play-store/ # Store listings (DE/EN) & Play Console Data Safety checklist
├── scripts/         # Automated test, build, sign, 16KB ELF align check & checksum script
├── ARCHITECTURE_ANALYSIS.md
├── PLAY_STORE_GUIDE.md
└── PRIVACY_POLICY.md
```

---

## Building Locally (APK & AAB)

Requirements: **JDK 21**, **Android SDK (API 36, Build-Tools 36.0.0)**.

1. Create `local.properties` (excluded from Git):
   ```properties
   sdk.dir=/path/to/Android/sdk
   ```
2. Optional release signing outside the repository (`~/.tapwing-signing/keystore.properties` or env vars `TAPWING_KEYSTORE_PATH`, `TAPWING_STORE_PASSWORD`, `TAPWING_KEY_ALIAS`, `TAPWING_KEY_PASSWORD`).
3. Run tests, build signed APK + AAB, verify signatures & 16 KB ELF alignment:
   ```bash
   ./scripts/build-release.sh
   ```
   Outputs are written to `dist/Tapwing-v1.2.0-release.apk`, `dist/Tapwing-v1.2.0-release.aab`, `dist/Tapwing-v1.2.0-mapping.txt`, and `dist/SHA256SUMS.txt`.

---

## Documentation & License

- [ARCHITECTURE_ANALYSIS.md](ARCHITECTURE_ANALYSIS.md) — Clean-room mapping & on-device vs. cloud AI breakdown
- [PLAY_STORE_GUIDE.md](PLAY_STORE_GUIDE.md) & [docs/play-store/](docs/play-store/) — Signing, Store Listings, Data Safety & Google Play submission checklist
- [PRIVACY_POLICY.md](PRIVACY_POLICY.md) — Bilingual EN/DE Privacy Policy
- [LICENSE](LICENSE) — Apache License 2.0
