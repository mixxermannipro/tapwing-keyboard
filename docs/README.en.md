<div align="center">

<img src="screenshots/tapwing-icon.png" width="96" alt="Tapwing app icon with a stylized wing on a key" />

# Tapwing

**Type, dictate and edit text straight from your Android keyboard.**

[Google Play · Closed test](https://play.google.com/apps/testing/com.tapwing.keyboard) · [Screenshots](../README.md#screenshots) · [Deutsch](../README.md)

</div>

Tapwing 2.6.1 supports Android 10 and later and focuses on ultra-fast private on-device dictation alongside a streamlined, Gboard-familiar Material 3 Expressive interface.

## What you can do

| Feature | What it does |
| --- | --- |
| **Typing and suggestions** | Switch languages and layouts, swipe to type, insert a suggested word and see likely continuations. Tapwing learns locally and can adapt suggestions by app. |
| **Dictation** | Dictate privately on-device with **Sherpa-ONNX** (**NVIDIA FastConformer German**, **Kroko live word-by-word streaming**, **NVIDIA Parakeet TDT 0.6B v3 / Ultra** for 25 EU languages, **NVIDIA Parakeet TDT 0.6B v2 English**, **NVIDIA Canary 180M Flash**) or **Whisper**, backed by automatic quiet-mic gain normalization, pre-warming on keyboard open, and Rambler-style cleanup of fillers, stutters, self-corrections, and voice commands. Standard Android speech recognition and optional cloud APIs remain supported. |
| **Writing tools** | Proofread, shorten or rephrase text with built-in rules, a downloadable local language model or an optional cloud provider with your own key. |
| **Clipboard and text scanning** | Paste from the suggestion bar, pin clips, set retention and scan text from the camera or a single selected photo. Copied verification codes can appear briefly as a one-tap suggestion. |
| **Dictionary and snippets** | Download offline dictionaries, import and export your own words and use snippets such as `/date` or your own abbreviations. |
| **Layout and shortcuts** | Minimalist Gboard-aligned settings with fluid spring transitions, theme customization, keyboard height, quick bar, one-handed or split layouts, text selection controls, inline math and typing statistics. Android inline autofill is supported on Android 11 and later. |

## Get started

1. Install Tapwing through the [closed Google Play test](https://play.google.com/apps/testing/com.tapwing.keyboard) or build it locally.
2. Open Tapwing, enable it in Android keyboard settings and select it as the input method.
3. Try the text field in Tapwing. To dictate offline at maximum speed, open **Dictation** and download the recommended Sherpa-ONNX model for your language.

## Data and privacy

Typing, learning, clipboard history and text recognition are processed on the device. Incognito mode pauses learning and clipboard history. Sherpa-ONNX, Whisper and Android offline process audio locally. Android's standard speech service may use the internet depending on the device.

Optional Gemini Live sends audio as you speak. The separate cloud dictation mode sends the recording after you stop. Cloud writing or transcript cleanup can send the text being processed to your configured provider. Tapwing has no ads or analytics SDKs. See the [privacy policy](../PRIVACY_POLICY.md) for permissions, storage and deletion.

## Build locally

Use **JDK 21**, **Android SDK 36** and **Build-Tools 36.0.0**. Set the SDK path in `local.properties` or let Android Studio set it up.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Find the debug APK at `app/build/outputs/apk/debug/app-debug.apk`. For signing and distribution, see the [Play Store guide](../PLAY_STORE_GUIDE.md). For implementation details, see the [architecture notes](../ARCHITECTURE_ANALYSIS.md).

File bugs and suggestions through [GitHub Issues](https://github.com/mixxermannipro/tapwing-keyboard/issues). Tapwing is licensed under [Apache 2.0](../LICENSE).
