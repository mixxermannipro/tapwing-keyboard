# Tapwing Licensing

Tapwing is published under a dual historical & source-available licensing model:

1. **Tapwing Source Available License (current code, post-`299dc28b80e791e750cff20ff10694b031d6604e`)**:
   All additions, refactoring, security hardening, AI/voice pipelines, and UI changes introduced after commit `299dc28b80e791e750cff20ff10694b031d6604e` are licensed exclusively under `LicenseRef-Tapwing-Source-Available` as defined in [LICENSE](../LICENSE).
2. **Apache-2.0 Historical Baseline (through `299dc28b80e791e750cff20ff10694b031d6604e`)**:
   The earlier codebase up to and including commit `299dc28b80e791e750cff20ff10694b031d6604e` remains available under the Apache License, Version 2.0.
3. **REUSE / SPDX Metadata**:
   Machine-readable license annotations live in [REUSE.toml](../REUSE.toml), with `SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available` headers on new source files.

## Bundled Third-Party Libraries & Preserved Notices
- Android release packaging explicitly preserves `META-INF/NOTICE*` and `META-INF/LICENSE*` notices for bundled libraries (AndroidX, Jetpack Compose, Kotlin Coroutines, Sherpa-ONNX, ONNX Runtime, Llamatik / llama.cpp, OkHttp, Apache Commons Compress, Google ML Kit).
- Full attribution details are viewable inside the app under **Settings -> System -> Open-Source & Model Licenses**.

## Downloadable On-Device Models & Dictionary Packs
- Speech models (NVIDIA NeMo FastConformer / Nemotron / Parakeet, Sherpa-ONNX Whisper, Kroko, Canary), local GGUF LLMs (LiquidAI LFM2.5, Qwen3, Gemma-3n, Gemma-3), and FrequencyWords dictionaries are **not bundled** inside the APK/AAB.
- Users optionally download them on demand from pinned upstream commits with strict SHA-256 and exact byte-size verification (`DownloadIntegrityVerifier`).
- Each catalog item carries its upstream license (`CC-BY-4.0`, `Apache-2.0`, `MIT`, `Gemma Terms of Use`, `NSLCL`, `CC-BY-SA-4.0`, `Qwen`), attribution notice, and clickable license link inside the app.
