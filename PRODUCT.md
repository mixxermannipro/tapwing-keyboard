# Tapwing — Product Overview

Tapwing is a fast, privacy-first Android keyboard (`com.tapwing.keyboard`) supporting German (`QWERTZ` including Swiss layout & optional umlauts) and English (`QWERTY` / `AZERTY`). It delivers modern typing productivity without ads, telemetry, or broad device permissions.

## Core Capabilities
- **Expressive Capsule Typing & Glass Mode**: Dedicated 1–0 number row, secondary symbol hints, continuous height slider (`75%–135%`), optional translucent Glass visual mode, high-contrast WCAG AAA key colors, and large key labels.
- **Smart Input & Dictionary Engine**: Local Damerau-Levenshtein + layout-weighted autocorrect, 1-tap Proactive Undo, swipe-backspace word deletion, inline calculator (`24*7=`), glide typing + 2D spacebar cursor, offline FrequencyWords packs (SHA-256 verified), and Gboard-export compatible `.zip`/`.tsv`/`.json` dictionary import & export.
- **On-Device Voice Dictation**: Offline speech recognition powered by Sherpa-ONNX (`NVIDIA FastConformer`, `Parakeet`, `Nemotron`, `Whisper`) plus Android on-device recognizer fallback, local natural filler/punctuation/bullet cleanup, and optional opt-in cloud dictation.
- **Writing Aid**: Instant offline rule-based proofreading, rewrite, tone adjustment, and translation, extensible with optional downloaded local GGUF models (auto-unloaded after 120s idle) or opt-in BYO-key cloud providers stored via hardware-backed Android Keystore.
- **Clipboard History, Camera OCR & Smart Fill**: Configurable clipboard retention (`1h` to `Never`), OTP & password exclusion, live CameraX + ML Kit text scanner, single-photo System Photo Picker OCR (zero storage permissions), and opt-in CE-encrypted Smart Fill profile suggestions.

## Supported ABI & Architecture Rationale
- Release APKs/AABs currently target **`arm64-v8a`** to ship modern 64-bit native inference engines (`Sherpa-ONNX` & `llama.cpp` / `Llamatik`) cleanly within compact AAB download limits while meeting Google Play's **16 KB ELF page-size alignment** requirement.
