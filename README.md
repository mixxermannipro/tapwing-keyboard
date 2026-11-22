# Tapwing Keyboard (`com.tapwing.keyboard`)

**Tapwing** is a clean-room, Apache-2.0 **Material 3 Expressive + Apple Liquid Glass** Android keyboard built in Kotlin and Jetpack Compose on top of official Android APIs (`InputMethodService`, `SpeechRecognizer`, CameraX + ML Kit Text Recognition, System Photo Picker, AndroidX DataStore, and hardware-backed `AndroidKeyStore`).

---

## Highlights (`v1.2.0`)

- **1:1 Pixel Slate & Material 3 Expressive Capsule UI**: Full stadium-pill keycaps with centered top symbol hints (`% \ | = [ ] < > { }`, `@ # € _ & - + ( )`, `* " ' : ; ! ?`), dedicated `1–0` number row, clean unmarked spacebar, 5 themes (`Pixel Slate`, `Expressive Lavender`, `Material You Dynamic`, `Pixel Blue`, `AMOLED Dark`), and a borderless bottom-right dismiss chevron (`⌄`).
- **Apple Mode (Liquid Glass)**: Toggleable translucent glass keycaps with vertical specular highlights, spring keycap animations, and floating preview bubbles — while preserving the exact same 1:1 key layout.
- Find features in **Settings** under Typing, Tools, or Keyboard and app. Search by name or keyword, try the keyboard in the test field, and follow recommendations based on your setup.
- **Customizable & Reorderable Quick-Actions Strip**: Choose and reorder up to 8 shortcuts above the keyboard (`QuickBarAction`), either directly inline via long-press / *Tools Hub > Edit bar* (`QuickBarCustomizePanel`) with horizontal drag-swap and move controls, or in *Settings > Quick Bar* (`QuickBarSettingsScreen`).
- **Gboard/PixelBoard `.zip` Dictionary Import/Export & Adaptive Learning**: 1:1 `.zip` (`dictionary.txt` with `# Gboard Dictionary version:1` / `version:2`), `.txt`, `.csv`/`.tsv`, `key => phrase`, and `.json` import plus `.zip` & `.json` export. Automatically learns unigrams, next-word bigrams, shortcuts (`/datum`, `/date`, `/zeit`, `/time`), and rejected autocorrections on-device.
- **Dynamic Live Resizing & Power Tools**: Smooth continuous height slider (`75%–135%`) + live interactive drag-border resizer, bottom offset control, **Cursor & Text Edit D-Pad** (`← ↑ ↓ →`, `Select`, `Copy/Cut/Paste`), **One-Handed Mode** (Left/Right), **Inline Math** (`24*7=` → `168`), **Swipe-Backspace Word Delete**, **In-Keyboard Quick Search**, and **Incognito Mode**.
- **Dictation**: Use Android offline for live text when your device has a supported language pack, or download Whisper to process audio on the device. Whisper can process completed phrases during recording when it detects clear pauses. For optional live cloud dictation, choose Gemini Live and save your own Google API key. The separate cloud mode sends audio after you stop, using your Gemini or OpenAI key. The key field includes setup instructions.
- Tap a **next-word suggestion** to insert it with a space and see likely continuations. Tapwing uses the current sentence, learned word sequences, and app-specific usage on the device. Turn off app-specific learning under Dictionary & Learning.
- **Undo** restores the text changed by a correction, suggestion, dictation, writing action, or paste while the edit still matches the current field. The clipboard keeps one entry per identical text or image. It preserves pinned items and skips clips Android marks as sensitive.
- **Autofill** on Android 11+ shows suggestions supplied by your password manager above the keys. In *Settings > Passwords and autofill*, open Android’s credential settings. For Bitwarden, select it as Android’s autofill service and enable Inline in Bitwarden. The site or app must expose supported sign-in fields.
- Choose a search engine and where to open results under *Settings > Quick search*. The default browser is used unless you choose a compatible installed app. You can also insert the search link into the current editor.
- **AI Writing Tools (Offline + Bring-Your-Own-Key)**: Inline *Proofread*, *Professional*, *Casual*, *Concise*, *Emotive*, and *Rephrase* modes via offline rules (`LocalWritingAssistant`) or optional **Google Gemini** / **OpenAI-compatible** API key encrypted in `AndroidKeyStore`. The AI settings link to provider model lists and let you copy the configured model ID.
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
