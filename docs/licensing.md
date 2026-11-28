# Tapwing Licensing

Tapwing is free and open-source software licensed under the **GNU General Public License v3.0 or later (`GPL-3.0-or-later`)**, with an additional permission under GPLv3 Section 7 for Google ML Kit and Google Play services libraries:

1. **Current License (`GPL-3.0-or-later` + Section 7 Exception)**:
   The Tapwing codebase is licensed under the GNU General Public License, Version 3, or (at your option) any later version, as set out in [LICENSE](../LICENSE) and [LICENSES/GPL-3.0-or-later.txt](../LICENSES/GPL-3.0-or-later.txt).
   Linking or combining Tapwing with Google ML Kit or Google Play services libraries is covered by the additional permission in [LICENSE-EXCEPTION.md](../LICENSE-EXCEPTION.md).
2. **Historical Apache-2.0 Baseline (through `299dc28b80e791e750cff20ff10694b031d6604e`)**:
   Up to and including commit `299dc28b80e791e750cff20ff10694b031d6604e`, the codebase was additionally available under the Apache License, Version 2.0 ([LICENSES/Apache-2.0.txt](../LICENSES/Apache-2.0.txt)), and remains available under Apache-2.0 in that historical revision.
3. **Trademarks**:
   The name **"Tapwing"** and the Tapwing logo/icon are not licensed under `GPL-3.0-or-later`. See [TRADEMARKS.md](../TRADEMARKS.md) for fork naming and branding rules.
4. **REUSE / SPDX Metadata**:
   Machine-readable license annotations live in [REUSE.toml](../REUSE.toml), with `SPDX-License-Identifier: GPL-3.0-or-later` headers on source files.

## Bundled Third-Party Libraries & Preserved Notices
- Android release packaging explicitly preserves `META-INF/NOTICE*` and `META-INF/LICENSE*` notices for bundled libraries (AndroidX, Jetpack Compose, Kotlin Coroutines, Sherpa-ONNX, ONNX Runtime, Llamatik / llama.cpp, OkHttp, Apache Commons Compress, Google ML Kit).
- Full attribution details are viewable inside the app under **Settings -> System -> Open-Source & Model Licenses**.

## Downloadable On-Device Models & Dictionary Packs
- Speech models (NVIDIA NeMo FastConformer / Nemotron / Parakeet, Sherpa-ONNX Whisper, Kroko, Canary), local GGUF LLMs (LiquidAI LFM2.5, Qwen3, Gemma-3n, Gemma-3), and FrequencyWords dictionaries are **not bundled** inside the APK/AAB.
- Users optionally download them on demand from pinned upstream commits with strict SHA-256 and exact byte-size verification (`DownloadIntegrityVerifier`).
- Each downloaded model and dictionary pack retains its own upstream license (`CC-BY-4.0`, `Apache-2.0`, `MIT`, `Gemma Terms of Use`, `NSLCL`, `CC-BY-SA-4.0`, `Qwen`), attribution notice, and clickable license link inside the app.
