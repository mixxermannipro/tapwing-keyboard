# Architecture & Feature Parity Analysis (Clean-Room Option 1)

Tapwing (`com.tapwing.keyboard`) is an independent, clean-room Android keyboard written in Kotlin and Jetpack Compose under the **Apache-2.0** license (`versionName = 1.2.0`, `versionCode = 4`, `minSdk = 29`, `targetSdk = 36`). It strictly uses official Android SDK APIs, Jetpack libraries, and Google ML Kit — with **zero** binary patching, **zero** signature bypasses, **zero** proprietary binaries/GPL code, and **zero** broad storage permissions.

---

## Feature & Architecture Mapping

| Feature Area | UX Target | Tapwing Clean-Room Implementation | Execution Mode |
| :--- | :--- | :--- | :--- |
| **Keyboard Core (IME)** | Standalone side-by-side keyboard | `TapwingInputMethodService` (`InputMethodService` + `LifecycleOwner` + `ComposeView` + `onUpdateSelection`) + `res/xml/method.xml` (`de_DE` QWERTZ & `en_US` QWERTY) | 100% On-Device |
| **Material 3 / Pixel Slate & Apple Liquid Glass** | 1:1 PixelBoard capsule keycaps (`q(%)`..`p(})`), dedicated `1–0` row, unmarked spacebar, borderless bottom-right dismiss chevron (`⌄`), dynamic `75%–135%` resize, plus optional Apple Liquid Glass & spring animations | `TapwingTheme.kt` (`PIXEL_SLATE`, `EXPRESSIVE_LAVENDER`, `DYNAMIC`, `PIXEL_BLUE`, `AMOLED_DARK`, `appleLiquidGlass`), `KeyboardGrid.kt`, `TapwingKeyboardRoot.kt` | 100% On-Device |
| **Customizable Quick-Actions Bar** | Pick & reorder top toolbar shortcuts directly on keyboard or in Settings | `QuickBarAction.kt` (persisted via DataStore `QUICK_BAR_ACTIONS`) + `QuickBarUiMeta.kt` + `SuggestionStrip.kt` (long-press editor) + `InlinePanels.kt` (`QuickBarCustomizePanel` horizontal drag-swap) + `QuickBarSettingsScreen` | 100% On-Device |
| **Custom Dictionary (`.zip`) & Adaptive Learning** | Import/export PixelBoard/Gboard `.zip` (`dictionary.txt` v1/v2), `.txt`, `.csv`/`.tsv`, `key => text`, `.json`; learn unigrams, next-word bigrams, shortcuts (`/datum`, `/zeit`), and undone corrections | `CustomDictionaryParser.kt` (`parseStream`/`parseBytes`/`exportToGboardZip`) + `AdaptiveDictionaryStore.kt` + `SuggestionEngine.kt` + `LocalWritingAssistant.kt` + `NaturalVoiceCleaner.kt` | 100% On-Device |
| **Power Productivity Tools** | Live drag/slider resize, Cursor & Select D-Pad, One-Handed mode, Inline Math (`24*7=`), Swipe-Backspace word delete, Incognito mode, Quick Search | `InlinePanels.kt` (`KeyboardResizePanel`, `TextEditPanel`, `QuickSearchPanel`) + `InlineMathEvaluator.kt` + `KeyboardGrid.kt` | 100% On-Device |
| **Natural Voice Dictation** | Filler word removal (`ähm`/`um`), self-corrections (`nein warte`/`wait no`), auto-punctuation, minimizable bar, 1.5s rotation grace, strict language lock | `TapwingVoiceController` (`android.speech.SpeechRecognizer` with `EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE`) + `NaturalVoiceCleaner` + `NaturalVoiceOnboardingSheet` | System Speech Service + On-Device Cleanup |
| **AI Writing Tools** | Proofread, Professional, Casual, Concise, Emotive, Rephrase chips + onboarding card | `WritingToolsPanel` + `WritingToolsEngine.kt`: defaults to `LocalWritingAssistant` (offline DE/EN rules); optionally routes to Gemini REST or OpenAI-compatible endpoints via `CloudAiClient` | On-Device by default; Cloud only with user API key |
| **Spellcheck, Grammar & Proactive Undo** | Suggestion strip chips, repeated-word/spacing/noun-cap hints, 1-tap Undo pill | `SuggestionEngine.kt` (with length-diff filter before Levenshtein) + `UndoManager.kt` + `SuggestionStrip.kt` | 100% On-Device |
| **Extended Clipboard** | Configurable retention (`1h`, `24h`, `7d`, `30d`, `Never`), up to `200` (default `100`) unpinned + pinned clips, 2-column grid | `ClipboardStore.kt` (JSON persisted in device-protected storage) + `ClipboardManager.OnPrimaryClipChangedListener` + `ClipboardPanel` | 100% On-Device |
| **Scan Text (Camera & Photo Picker OCR)** | Live CameraX OCR viewfinder + privacy-first single-image Photo Picker OCR (`PickVisualMedia`, zero gallery storage permissions) | `ScanTextActivity.kt` (CameraX + Google ML Kit Text Recognition `16.0.1` + `PickVisualMedia`) + `ScreenshotMonitor.kt` + `OcrResultBus` | 100% On-Device |
| **Emoji Panel & Scaling** | Category tabs, scaled emoji grid (`20..34 sp`), emoji candidate chips | `EmojiCatalog.kt` + `EmojiPanel` + `SuggestionEngine` emoji triggers | 100% On-Device |
| **Minimalist Apple-Style Settings & Back-Swipe** | Clean inset-grouped iOS-style Settings, hairline dividers, search pill, borderless fields, compact live test field, level-by-level `BackHandler` predictive back navigation | `SettingsActivity.kt` + `AppleSettingsComponents.kt` + `SettingsScreens.kt` + `enableOnBackInvokedCallback="true"` + `TapwingPreferencesRepository` | 100% On-Device |
| **Secret Storage & Play Store Compliance** | Encrypted optional API key storage, 16 KB ELF native library alignment, R8 mapping + `SYMBOL_TABLE` native debug symbols | `SecureKeyStorage.kt` (`AndroidKeyStore` AES-GCM-256), `build.gradle.kts`, `scripts/build-release.sh` | 100% On-Device (Hardware-backed Keystore) |

---

## Transparency: On-Device vs. User-Configured Cloud AI

- **Out of the box (no key required):** Every feature — typing, QWERTZ/QWERTY layouts, autocorrect, grammar hints, Proactive Undo, extended clipboard, camera/Photo-Picker OCR, `.zip` dictionary import/export, voice filler/punctuation cleanup, and basic DE/EN Writing Tools (`LocalWritingAssistant`) — works completely offline/locally.
- **With user API key:** In *Settings > Writing Tools*, users can select **Google Gemini API** (default model `gemini-2.5-flash`) or any **OpenAI-compatible endpoint**, store their own API key encrypted in Android Keystore, and test the connection live. API keys are sent strictly in HTTP headers (`x-goog-api-key` or `Authorization: Bearer`) and are never logged or committed to source control.
