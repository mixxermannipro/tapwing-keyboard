# Architecture & Feature Parity Analysis (Clean-Room Option 1)

Tapwing (`com.tapwing.keyboard`) is an independent, clean-room Android keyboard written in Kotlin and Jetpack Compose under the **Apache-2.0** license. It reproduces the visual ergonomics and productivity workflows popularized by PixelBoard while strictly using official Android SDK APIs, Jetpack libraries, and Google ML Kit — with **zero** binary patching, **zero** signature bypasses, and **zero** proprietary Google/Gboard binaries or GPL code.

---

## Feature & Architecture Mapping

| Feature Area | UX Target | Tapwing Clean-Room Implementation | Execution Mode |
| :--- | :--- | :--- | :--- |
| **Keyboard Core (IME)** | Standalone side-by-side keyboard | `TapwingInputMethodService` (`InputMethodService` + `LifecycleOwner` + `ComposeView`) + `res/xml/method.xml` (`de_DE` QWERTZ & `en_US` QWERTY) | 100% On-Device |
| **Material 3 / Pixel Slate & Apple Liquid Glass** | 1:1 PixelBoard capsule keycaps (`q(%)`..`p(})`), dedicated `1–0` row, unmarked spacebar, borderless bottom-right dismiss chevron (`⌄`), dynamic `75%–135%` resize, plus optional Apple Liquid Glass & spring animations | `TapwingTheme.kt` (`PIXEL_SLATE`, `EXPRESSIVE_LAVENDER`, `DYNAMIC`, `PIXEL_BLUE`, `AMOLED_DARK`, `appleLiquidGlass`), `KeyboardGrid.kt`, `TapwingKeyboardRoot.kt` | 100% On-Device |
| **Custom Dictionary & Adaptive Learning** | Import/export `.txt`, `.csv`, `.tsv` (Gboard export), `key => text`, `.json`; learn unigrams, next-word bigrams, shortcuts (`/datum`, `/zeit`), and undone corrections | `CustomDictionaryParser.kt` + `AdaptiveDictionaryStore.kt` + `SuggestionEngine.kt` + `LocalWritingAssistant.kt` + `NaturalVoiceCleaner.kt` | 100% On-Device |
| **Power Productivity Tools** | Live drag/slider resize, Cursor & Select D-Pad, One-Handed mode, Inline Math (`24*7=`), Swipe-Backspace word delete, Incognito mode, Quick Search | `InlinePanels.kt` (`KeyboardResizePanel`, `TextEditPanel`, `QuickSearchPanel`) + `InlineMathEvaluator.kt` + `KeyboardGrid.kt` | 100% On-Device |
| **Natural Voice Dictation** | Filler word removal (`ähm`/`um`), self-corrections (`nein warte`/`wait no`), auto-punctuation, minimizable bar, 1.5s rotation grace, strict language lock | `TapwingVoiceController` (`android.speech.SpeechRecognizer` with `EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE`) + `NaturalVoiceCleaner` + `NaturalVoiceOnboardingSheet` | System Speech Service + On-Device Cleanup |
| **AI Writing Tools** | Inline white preview card + Proofread, Professional, Casual, Concise, Emotive, Rephrase chips + onboarding card | `WritingToolsPanel.kt` + `WritingToolsEngine.kt`: defaults to `LocalWritingAssistant` (offline DE/EN rules); optionally routes to Gemini REST or OpenAI-compatible endpoints via `CloudAiClient` | On-Device by default; Cloud only with user API key |
| **Spellcheck, Grammar & Proactive Undo** | Suggestion strip chips, repeated-word/spacing/noun-cap hints, 1-tap Undo pill | `SuggestionEngine.kt` + `UndoManager.kt` + `SuggestionStrip.kt` | 100% On-Device |
| **Extended Clipboard** | Configurable retention (`1h`, `24h`, `7d`, `30d`, `Never`), up to `100` unpinned + pinned clips, 2-column grid | `ClipboardStore.kt` (JSON persisted in device-protected storage) + `ClipboardManager.OnPrimaryClipChangedListener` + `ClipboardPanel.kt` | 100% On-Device |
| **Scan Text (Camera OCR) & Screenshot Chip** | Live camera OCR viewfinder + floating chip for recent screenshots | `ScanTextActivity.kt` (CameraX + Google ML Kit Text Recognition `16.0.1`) + `ScreenshotMonitor.kt` (MediaStore) + `OcrResultBus` | 100% On-Device |
| **Emoji Panel & Scaling** | Category tabs, scaled emoji grid (`20..34 sp`), emoji candidate chips | `EmojiCatalog.kt` + `EmojiPanel` + `SuggestionEngine` emoji triggers | 100% On-Device |
| **Settings & Advanced Settings** | Grouped rounded cards, search bar, live IME status, built-in test field, Advanced Settings with top-right reload button | `SettingsActivity.kt` + `SettingsScreens.kt` + `TapwingPreferencesRepository` (DataStore in device-protected storage) | 100% On-Device |
| **Secret Storage** | Safe storage for optional user API key | `SecureKeyStorage.kt` using `AndroidKeyStore` (`AES/GCM/NoPadding`, 256-bit key, `UserManager.isUserUnlocked()` guard) | 100% On-Device (Hardware-backed Keystore) |

---

## Transparency: On-Device vs. User-Configured Cloud AI

- **Out of the box (no key required):** Every feature — typing, QWERTZ/QWERTY layouts, autocorrect, grammar hints, Proactive Undo, extended 100-clip clipboard, camera OCR, screenshot text extraction, voice filler/punctuation cleanup, and basic DE/EN Writing Tools (`LocalWritingAssistant`) — works completely offline/locally.
- **With user API key:** In *Settings > AI Writing Tools*, users can select **Google Gemini API** (default model `gemini-2.5-flash`) or any **OpenAI-compatible endpoint**, store their own API key encrypted in Android Keystore, and test the connection live. API keys are sent strictly in HTTP headers (`x-goog-api-key` or `Authorization: Bearer`) and are never logged or committed to source control.
