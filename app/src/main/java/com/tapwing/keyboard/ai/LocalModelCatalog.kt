package com.tapwing.keyboard.ai

import com.tapwing.keyboard.data.KeyboardLocales

enum class LocalModelKind {
    WHISPER_STT,
    SHERPA_STREAMING,
    SHERPA_TRANSDUCER,
    SHERPA_CANARY,
    LOCAL_LLM;

    val isSherpa: Boolean
        get() = this == SHERPA_STREAMING || this == SHERPA_TRANSDUCER || this == SHERPA_CANARY

    val isSpeechModel: Boolean
        get() = this != LOCAL_LLM

    val isLlm: Boolean
        get() = this == LOCAL_LLM
}

data class LocalModelSpec(
    val id: String,
    val kind: LocalModelKind,
    val displayName: String,
    val fileName: String,
    val approxSizeMb: Int,
    val minRamGb: Int,
    val downloadUrl: String,
    val repoUrl: String,
    val isRecommended: Boolean = false,
    val summaryDe: String,
    val summaryEn: String,
    val requiredFiles: List<String> = emptyList(),
    val supportedLanguages: Set<String> = emptySet(),
    val speedBadgeDe: String = "",
    val speedBadgeEn: String = "",
    val highlightTagDe: String = "",
    val highlightTagEn: String = "",
    val nativePunctuation: Boolean = false,
    val supportsStreaming: Boolean = false,
    val supportsMultilingualCodeSwitching: Boolean = false,
    val streamingModelType: String = "zipformer2",
    val supportsNoThinkTag: Boolean = false,
    val isFlagshipTier: Boolean = false,
    val shortEngineLabel: String = "Whisper",
    val exactSizeBytes: Long = 0L,
    val sha256: String = "",
    val licenseName: String = "",
    val licenseUrl: String = "",
    val attributionNotice: String = "",
    val commercialUseNote: String = ""
) {
    fun supportsLocale(locale: String): Boolean {
        if (supportedLanguages.isEmpty()) return true
        return KeyboardLocales.baseLang(locale) in supportedLanguages
    }
}

object LocalModelCatalog {

    private val EUROPEAN_25_LANGS = setOf(
        "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de",
        "el", "hu", "it", "lv", "lt", "mt", "pl", "pt", "ro", "sk",
        "sl", "es", "sv", "ru", "uk"
    )

    private val NEMOTRON_MULTILINGUAL_LANGS = setOf(
        "en", "es", "fr", "it", "pt", "nl", "de", "tr", "ru", "ar",
        "hi", "ja", "ko", "vi", "uk", "pl", "sv", "cs", "nb", "da",
        "bg", "fi", "hr", "sk", "zh", "hu", "ro", "et"
    )

    val sherpaModels: List<LocalModelSpec> = listOf(
        LocalModelSpec(
            id = "sherpa-fastconformer-de-pc-int8",
            kind = LocalModelKind.SHERPA_TRANSDUCER,
            displayName = "NVIDIA FastConformer Deutsch (Int8)",
            fileName = "sherpa-onnx-nemo-transducer-stt_de_fastconformer_hybrid_large_pc-int8.tar.bz2",
            approxSizeMb = 103,
            minRamGb = 3,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-transducer-stt_de_fastconformer_hybrid_large_pc-int8.tar.bz2",
            repoUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-stt_de_fastconformer_hybrid_large_pc-int8",
            isRecommended = true,
            summaryDe = "6–10× schneller als Whisper mit eingebauten Satzzeichen & Groß-/Kleinschreibung. Ideal für reines Deutsch.",
            summaryEn = "6–10x faster than Whisper with built-in punctuation & capitalization. Best speed/accuracy for German.",
            requiredFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
            supportedLanguages = setOf("de"),
            speedBadgeDe = "⚡ ~0,25 Sek.",
            speedBadgeEn = "⚡ ~0.25s",
            highlightTagDe = "Schnell · Deutsch",
            highlightTagEn = "Fast · German",
            nativePunctuation = true,
            supportsStreaming = false,
            shortEngineLabel = "FastConformer",
            exactSizeBytes = 108_124_747L,
            sha256 = "73b3cce9f94a19fc04bc54b55abd576b622e3925a84ddd554a95e5984bc11677",
            licenseName = "CC-BY-4.0",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            attributionNotice = "NVIDIA NeMo FastConformer / k2-fsa Sherpa-ONNX",
            commercialUseNote = "Permitted with attribution (CC-BY-4.0)"
        ),
        LocalModelSpec(
            id = "sherpa-nemotron-3.5-streaming-multilingual-int8",
            kind = LocalModelKind.SHERPA_STREAMING,
            displayName = "NVIDIA Nemotron 3.5 Live Multilingual (Int8)",
            fileName = "sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11.tar.bz2",
            approxSizeMb = 470,
            minRamGb = 6,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11.tar.bz2",
            repoUrl = "https://k2-fsa.github.io/sherpa/onnx/nemo/nemotron-streaming.html",
            summaryDe = "Echtzeit-Streaming für 28+ Sprachen inkl. Deutsch + Englisch gemischt, automatische Spracherkennung & native Satzzeichen.",
            summaryEn = "Real-time live streaming across 28+ languages with seamless DE/EN code-switching, auto language ID & native punctuation.",
            requiredFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
            supportedLanguages = NEMOTRON_MULTILINGUAL_LANGS,
            speedBadgeDe = "⚡ Live · Mehrsprachig",
            speedBadgeEn = "⚡ Live · Multilingual",
            highlightTagDe = "Neu · Live DE+EN",
            highlightTagEn = "New · Live DE+EN",
            nativePunctuation = true,
            supportsStreaming = true,
            supportsMultilingualCodeSwitching = true,
            streamingModelType = "",
            shortEngineLabel = "Nemotron Live",
            exactSizeBytes = 475_271_763L,
            sha256 = "c6bf5e0df765f9d5b43bc9e0536d4b4b3e7d40bdf5ecf13e45f134c51c05ae3a",
            licenseName = "NVIDIA Open Model License",
            licenseUrl = "https://www.nvidia.com/en-us/agreements/enterprise-software/nvidia-open-model-license/",
            attributionNotice = "NVIDIA Nemotron ASR / k2-fsa Sherpa-ONNX",
            commercialUseNote = "Permitted under NVIDIA Open Model License"
        ),
        LocalModelSpec(
            id = "sherpa-streaming-zipformer-de",
            kind = LocalModelKind.SHERPA_STREAMING,
            displayName = "Kroko Live-Streaming Deutsch",
            fileName = "sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06.tar.bz2",
            approxSizeMb = 54,
            minRamGb = 2,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06.tar.bz2",
            repoUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06",
            summaryDe = "Schreibt Wort für Wort live beim Sprechen – fast keine Wartezeit nach ✓. Ideal auch für ältere Handys.",
            summaryEn = "Streams words live as you speak with near-zero wait after ✓. Compact 54 MB for any phone.",
            requiredFiles = listOf("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt"),
            supportedLanguages = setOf("de"),
            speedBadgeDe = "⚡ Live · < 0,1 Sek.",
            speedBadgeEn = "⚡ Live · < 0.1s",
            highlightTagDe = "Echtzeit · 54 MB",
            highlightTagEn = "Real-time · 54 MB",
            nativePunctuation = false,
            supportsStreaming = true,
            streamingModelType = "zipformer2",
            shortEngineLabel = "Live",
            exactSizeBytes = 57_565_698L,
            sha256 = "9e27b783c20e67b0d0f13a258c1861fce199917c969d9176a438bee38df64962",
            licenseName = "CC-BY-SA-4.0 (Kroko Community)",
            licenseUrl = "https://huggingface.co/Banafo/Kroko-ASR/blob/main/LICENSE",
            attributionNotice = "Banafo Kroko ASR / k2-fsa Sherpa-ONNX",
            commercialUseNote = "Community model under CC-BY-SA-4.0 (downloaded directly by user)"
        ),
        LocalModelSpec(
            id = "sherpa-parakeet-ultra-0.6b-int8",
            kind = LocalModelKind.SHERPA_TRANSDUCER,
            displayName = "NVIDIA Parakeet Ultra 0.6B (Int8)",
            fileName = "sherpa-onnx-nemo-parakeet-tdt-0.6b-ultra-int8.tar.bz2",
            approxSizeMb = 464,
            minRamGb = 6,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-ultra-int8.tar.bz2",
            repoUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-ultra-int8",
            summaryDe = "Optimierte Weiterentwicklung von NVIDIA Parakeet v3 mit geringster Fehlerrate für Deutsch, Englisch & Mischsprache (25 EU-Sprachen).",
            summaryEn = "Fine-tuned NVIDIA Parakeet v3 with lowest WER in German, English & code-switching across 25 EU languages.",
            requiredFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
            supportedLanguages = EUROPEAN_25_LANGS,
            speedBadgeDe = "⚡ ~0,35 Sek.",
            speedBadgeEn = "⚡ ~0.35s",
            highlightTagDe = "Top DE+EN · 25 Sprachen",
            highlightTagEn = "Top DE+EN · 25 languages",
            nativePunctuation = true,
            supportsStreaming = false,
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "Parakeet",
            exactSizeBytes = 487_134_290L,
            sha256 = "d216b71d7f628b3f2f2a20ee7ead39ff0b075984959eb4bf6ddf8dc8da737c09",
            licenseName = "CC-BY-4.0",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            attributionNotice = "NVIDIA NeMo Parakeet / k2-fsa Sherpa-ONNX",
            commercialUseNote = "Permitted with attribution (CC-BY-4.0)"
        ),
        LocalModelSpec(
            id = "sherpa-parakeet-v3-0.6b-int8",
            kind = LocalModelKind.SHERPA_TRANSDUCER,
            displayName = "NVIDIA Parakeet TDT 0.6B v3 (Int8)",
            fileName = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2",
            approxSizeMb = 465,
            minRamGb = 6,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2",
            repoUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
            summaryDe = "Offizielles NVIDIA-Originalmodell für 25 europäische Sprachen mit automatischer Spracherkennung & eingebauten Satzzeichen.",
            summaryEn = "Official NVIDIA checkpoint for 25 European languages with automatic language detection and native punctuation.",
            requiredFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
            supportedLanguages = EUROPEAN_25_LANGS,
            speedBadgeDe = "⚡ ~0,35 Sek.",
            speedBadgeEn = "⚡ ~0.35s",
            highlightTagDe = "NVIDIA · 25 Sprachen",
            highlightTagEn = "NVIDIA · 25 languages",
            nativePunctuation = true,
            supportsStreaming = false,
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "Parakeet v3",
            exactSizeBytes = 487_211_443L,
            sha256 = "1495e91b59d7fc53ddca91f3431758ec99f8fa253fdb0e1e851be0f72b14b0f9",
            licenseName = "CC-BY-4.0",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            attributionNotice = "NVIDIA NeMo Parakeet v3 / k2-fsa Sherpa-ONNX",
            commercialUseNote = "Permitted with attribution (CC-BY-4.0)"
        ),
        LocalModelSpec(
            id = "sherpa-parakeet-v2-en-int8",
            kind = LocalModelKind.SHERPA_TRANSDUCER,
            displayName = "NVIDIA Parakeet TDT 0.6B v2 Englisch (Int8)",
            fileName = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2",
            approxSizeMb = 460,
            minRamGb = 6,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2",
            repoUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8",
            summaryDe = "Offizielles NVIDIA-Englischmodell mit geringster Fehlerrate im Englischen (6,5 % WER) und nativer Zeichensetzung.",
            summaryEn = "Official NVIDIA English flagship with top English accuracy (6.5% WER) and built-in punctuation.",
            requiredFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
            supportedLanguages = setOf("en"),
            speedBadgeDe = "⚡ ~0,35 Sek.",
            speedBadgeEn = "⚡ ~0.35s",
            highlightTagDe = "Beste Englisch-Qualität · NVIDIA",
            highlightTagEn = "Best English accuracy · NVIDIA",
            nativePunctuation = true,
            supportsStreaming = false,
            shortEngineLabel = "Parakeet v2",
            exactSizeBytes = 482_440_461L,
            sha256 = "6fc4426b54aa549a0019b8a75b8dea59164e71790a37ac56a045ad57f915f714",
            licenseName = "CC-BY-4.0",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            attributionNotice = "NVIDIA NeMo Parakeet v2 / k2-fsa Sherpa-ONNX",
            commercialUseNote = "Permitted with attribution (CC-BY-4.0)"
        ),
        LocalModelSpec(
            id = "sherpa-canary-180m-flash-int8",
            kind = LocalModelKind.SHERPA_CANARY,
            displayName = "NVIDIA Canary 180M Flash (Int8)",
            fileName = "sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8.tar.bz2",
            approxSizeMb = 146,
            minRamGb = 3,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8.tar.bz2",
            repoUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8",
            summaryDe = "Schnelles 4-Sprachen-Modell (Deutsch, Englisch, Französisch, Spanisch) mit nativer Zeichensetzung.",
            summaryEn = "Fast 4-language model (DE, EN, FR, ES) with built-in punctuation and capitalization.",
            requiredFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "tokens.txt"),
            supportedLanguages = setOf("de", "en", "fr", "es"),
            speedBadgeDe = "⚡ ~0,30 Sek.",
            speedBadgeEn = "⚡ ~0.30s",
            highlightTagDe = "Kompakt · DE/EN/FR/ES",
            highlightTagEn = "Compact · DE/EN/FR/ES",
            nativePunctuation = true,
            supportsStreaming = false,
            shortEngineLabel = "Canary",
            exactSizeBytes = 153_692_328L,
            sha256 = "7a38ed8b13f014ad632b09ff8d22e0c6f1359dd046af9235d281dfae841b9ab9",
            licenseName = "CC-BY-4.0",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            attributionNotice = "NVIDIA Canary 180M Flash / k2-fsa Sherpa-ONNX",
            commercialUseNote = "Permitted with attribution (CC-BY-4.0)"
        )
    )

    val whisperModels: List<LocalModelSpec> = listOf(
        LocalModelSpec(
            id = "whisper-base-q5_1",
            kind = LocalModelKind.WHISPER_STT,
            displayName = "Whisper Base (Q5_1)",
            fileName = "ggml-base-q5_1.bin",
            approxSizeMb = 60,
            minRamGb = 3,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base-q5_1.bin",
            repoUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            isRecommended = true,
            summaryDe = "Schnell und kompakt für alle 99 Sprachen & gemischtes Sprechen auf älteren Geräten.",
            summaryEn = "Fast and lightweight for 99 languages & code-switching on older phones.",
            speedBadgeDe = "~0,8–1,5 Sek.",
            speedBadgeEn = "~0.8–1.5s",
            highlightTagDe = "99 Sprachen · 60 MB",
            highlightTagEn = "99 languages · 60 MB",
            nativePunctuation = true,
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "Whisper",
            exactSizeBytes = 59_707_625L,
            sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
            licenseName = "MIT",
            licenseUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            attributionNotice = "OpenAI Whisper / Georgi Gerganov whisper.cpp",
            commercialUseNote = "Permitted (MIT)"
        ),
        LocalModelSpec(
            id = "whisper-tiny-q5_1",
            kind = LocalModelKind.WHISPER_STT,
            displayName = "Whisper Tiny (Q5_1)",
            fileName = "ggml-tiny-q5_1.bin",
            approxSizeMb = 32,
            minRamGb = 2,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-tiny-q5_1.bin",
            repoUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            summaryDe = "Minimaler Speicherbedarf (~32 MB), schnelle Fallback-Option für alle Sprachen.",
            summaryEn = "Ultra-compact (~32 MB), fast universal fallback.",
            speedBadgeDe = "~0,5–1,0 Sek.",
            speedBadgeEn = "~0.5–1.0s",
            highlightTagDe = "Ultra-Kompakt · 32 MB",
            highlightTagEn = "Ultra-compact · 32 MB",
            nativePunctuation = true,
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "Whisper",
            exactSizeBytes = 32_152_673L,
            sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
            licenseName = "MIT",
            licenseUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            attributionNotice = "OpenAI Whisper / Georgi Gerganov whisper.cpp",
            commercialUseNote = "Permitted (MIT)"
        ),
        LocalModelSpec(
            id = "whisper-small-q5_1",
            kind = LocalModelKind.WHISPER_STT,
            displayName = "Whisper Small (Q5_1)",
            fileName = "ggml-small-q5_1.bin",
            approxSizeMb = 190,
            minRamGb = 4,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-small-q5_1.bin",
            repoUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            summaryDe = "Stark bei Fachbegriffen, Namen & mehrsprachigem Diktat (DE+EN gemischt).",
            summaryEn = "Strong for names, technical terms & bilingual dictation across 99 languages.",
            speedBadgeDe = "~1,5–3,0 Sek.",
            speedBadgeEn = "~1.5–3.0s",
            highlightTagDe = "99 Sprachen · 190 MB",
            highlightTagEn = "99 languages · 190 MB",
            nativePunctuation = true,
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "Whisper",
            exactSizeBytes = 190_085_487L,
            sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
            licenseName = "MIT",
            licenseUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            attributionNotice = "OpenAI Whisper / Georgi Gerganov whisper.cpp",
            commercialUseNote = "Permitted (MIT)"
        ),
        LocalModelSpec(
            id = "whisper-large-v3-turbo-q5_0",
            kind = LocalModelKind.WHISPER_STT,
            displayName = "Whisper Large-v3 Turbo (Q5_0)",
            fileName = "ggml-large-v3-turbo-q5_0.bin",
            approxSizeMb = 574,
            minRamGb = 8,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-large-v3-turbo-q5_0.bin",
            repoUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            summaryDe = "Maximale Whisper-Genauigkeit für 99 Sprachen & Mischsprache (Geräte mit 8+ GB RAM).",
            summaryEn = "Maximum Whisper accuracy across 99 languages & code-switching (8+ GB RAM).",
            speedBadgeDe = "~2,5–5,0 Sek.",
            speedBadgeEn = "~2.5–5.0s",
            highlightTagDe = "Flaggschiff · 574 MB",
            highlightTagEn = "Flagship · 574 MB",
            nativePunctuation = true,
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "Whisper",
            exactSizeBytes = 574_041_195L,
            sha256 = "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
            licenseName = "MIT",
            licenseUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            attributionNotice = "OpenAI Whisper / Georgi Gerganov whisper.cpp",
            commercialUseNote = "Permitted (MIT)"
        )
    )

    /**
     * Curated 2026 on-device GGUF language models optimized for smartphone CPUs via Llamatik / llama.cpp.
     * Every architecture (lfm2, qwen35, gemma3) is natively compiled in Llamatik 1.10.1.
     */
    val llmModels: List<LocalModelSpec> = listOf(
        LocalModelSpec(
            id = "llm-lfm2.5-1.2b-q4km",
            kind = LocalModelKind.LOCAL_LLM,
            displayName = "LiquidAI LFM2.5 1.2B Instruct (Q4_K_M)",
            fileName = "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            approxSizeMb = 731,
            minRamGb = 4,
            downloadUrl = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/8ed288026e23958ad9dfa92d53ed773a8eee7125/LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF",
            isRecommended = true,
            summaryDe = "Hybrid-Architektur (Conv+Attention) speziell für Smartphones: extrem schnell (~40–60 tok/s) mit erstklassigem Deutsch & Englisch für Textbereinigung & Schreibhilfe.",
            summaryEn = "Hybrid mobile architecture (~40–60 tok/s on phone CPUs) with crisp German & English rewriting, proofreading, and dictation cleanup.",
            speedBadgeDe = "⚡ ~0,6–1,2 Sek.",
            speedBadgeEn = "⚡ ~0.6–1.2s",
            highlightTagDe = "Empfohlen · Ultra-Schnell",
            highlightTagEn = "Recommended · Ultra-Fast",
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "LFM2.5 1.2B",
            exactSizeBytes = 730_895_168L,
            sha256 = "b1b3de114215d9507409a662a501a631095a479a419584e8a2ded6304b19b4f5",
            licenseName = "LFM Open License v1.0",
            licenseUrl = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/blob/8ed288026e23958ad9dfa92d53ed773a8eee7125/LICENSE",
            attributionNotice = "Liquid AI LFM2.5",
            commercialUseNote = "Free commercial use under LFM 1.0 terms"
        ),
        LocalModelSpec(
            id = "llm-qwen3.5-0.8b-q4km",
            kind = LocalModelKind.LOCAL_LLM,
            displayName = "Qwen 3.5 0.8B (Q4_K_M)",
            fileName = "Qwen3.5-0.8B-Q4_K_M.gguf",
            approxSizeMb = 533,
            minRamGb = 3,
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/6ab461498e2023f6e3c1baea90a8f0fe38ab64d0/Qwen3.5-0.8B-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF",
            summaryDe = "Kompaktes 2026-Modell (533 MB) für jedes Smartphone. Blitzschnell im Direktmodus für Korrektur, Stichpunkte & Diktat-Glättung in 200+ Sprachen.",
            summaryEn = "Compact 2026 model (533 MB) for any phone. Blazing fast non-thinking cleanup, bullet lists & rewrites in 200+ languages.",
            speedBadgeDe = "⚡ ~0,4–0,9 Sek.",
            speedBadgeEn = "⚡ ~0.4–0.9s",
            highlightTagDe = "Kompakt · 533 MB",
            highlightTagEn = "Compact · 533 MB",
            supportsMultilingualCodeSwitching = true,
            supportsNoThinkTag = true,
            shortEngineLabel = "Qwen 3.5 0.8B",
            exactSizeBytes = 532_517_120L,
            sha256 = "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517",
            licenseName = "Apache-2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            attributionNotice = "Alibaba Qwen Team / Unsloth GGUF",
            commercialUseNote = "Permitted (Apache-2.0)"
        ),
        LocalModelSpec(
            id = "llm-qwen3.5-2b-q4km",
            kind = LocalModelKind.LOCAL_LLM,
            displayName = "Qwen 3.5 2B (Q4_K_M)",
            fileName = "Qwen3.5-2B-Q4_K_M.gguf",
            approxSizeMb = 1280,
            minRamGb = 6,
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/f6d5376be1edb4d416d56da11e5397a961aca8ae/Qwen3.5-2B-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF",
            summaryDe = "Cloud-Niveau direkt auf dem Handy: überragende Grammatik, Tonfall-Wechsel, Stichpunkte & DE+EN Mischsprache (Apache-2.0).",
            summaryEn = "Cloud-grade quality on device: outstanding grammar, tone shifts, bullet lists & multilingual code-switching (Apache-2.0).",
            speedBadgeDe = "⚡ ~1,0–2,0 Sek.",
            speedBadgeEn = "⚡ ~1.0–2.0s",
            highlightTagDe = "Beste Balance · Cloud-Niveau",
            highlightTagEn = "Best Balance · Cloud-Grade",
            supportsMultilingualCodeSwitching = true,
            supportsNoThinkTag = true,
            shortEngineLabel = "Qwen 3.5 2B",
            exactSizeBytes = 1_280_835_840L,
            sha256 = "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
            licenseName = "Apache-2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            attributionNotice = "Alibaba Qwen Team / Unsloth GGUF",
            commercialUseNote = "Permitted (Apache-2.0)"
        ),
        LocalModelSpec(
            id = "llm-gemma3-1b-q4km",
            kind = LocalModelKind.LOCAL_LLM,
            displayName = "Google Gemma 3 1B IT (Q4_K_M)",
            fileName = "gemma-3-1b-it-Q4_K_M.gguf",
            approxSizeMb = 806,
            minRamGb = 4,
            downloadUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/f0b45be0aac41bd6a100a4b5734cad5f67255bfb/gemma-3-1b-it-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF",
            summaryDe = "Leichtes Google-Modell mit sehr natürlichem Stil beim Umformulieren, Korrigieren & Zusammenfassen (140+ Sprachen).",
            summaryEn = "Lightweight Google model delivering natural phrasing for proofreading, rephrasing & summarizing across 140+ languages.",
            speedBadgeDe = "⚡ ~0,6–1,3 Sek.",
            speedBadgeEn = "⚡ ~0.6–1.3s",
            highlightTagDe = "Google · 806 MB",
            highlightTagEn = "Google · 806 MB",
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "Gemma 3 1B",
            exactSizeBytes = 806_058_272L,
            sha256 = "8270790f3ab69fdfe860b7b64008d9a19986d8df7e407bb018184caa08798ebd",
            licenseName = "Gemma Terms of Use",
            licenseUrl = "https://ai.google.dev/gemma/terms",
            attributionNotice = "Google Gemma 3 / Unsloth GGUF",
            commercialUseNote = "Permitted under Gemma Terms of Use"
        ),
        LocalModelSpec(
            id = "llm-lfm2.5-2.6b-q4km",
            kind = LocalModelKind.LOCAL_LLM,
            displayName = "LiquidAI LFM2.5 2.6B (Q4_K_M)",
            fileName = "LFM2.5-2.6B-Q4_K_M.gguf",
            approxSizeMb = 1670,
            minRamGb = 6,
            downloadUrl = "https://huggingface.co/LiquidAI/LFM2.5-2.6B-GGUF/resolve/e7caca5d835a3901a8e0d63e94009429bafafdfc/LFM2.5-2.6B-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/LiquidAI/LFM2.5-2.6B-GGUF",
            summaryDe = "Flaggschiff-Hybridmodell (Aug. 2026): spielt auf dem Niveau großer Cloud-Modelle bei ~30 tok/s auf modernen Smartphones.",
            summaryEn = "Flagship 2026 hybrid model rivaling much larger cloud models at ~30 tok/s on modern smartphone CPUs.",
            speedBadgeDe = "⚡ ~1,2–2,2 Sek.",
            speedBadgeEn = "⚡ ~1.2–2.2s",
            highlightTagDe = "Flaggschiff · Schnell",
            highlightTagEn = "Flagship · Fast",
            supportsMultilingualCodeSwitching = true,
            isFlagshipTier = true,
            shortEngineLabel = "LFM2.5 2.6B",
            exactSizeBytes = 1_674_455_040L,
            sha256 = "02a8b7e17487d326e46d68ce0ba24211e1b80a14c4cd0597fa73c1cd697f52ed",
            licenseName = "LFM Open License v1.0",
            licenseUrl = "https://huggingface.co/LiquidAI/LFM2.5-2.6B-GGUF/blob/e7caca5d835a3901a8e0d63e94009429bafafdfc/LICENSE",
            attributionNotice = "Liquid AI LFM2.5",
            commercialUseNote = "Free commercial use under LFM 1.0 terms"
        ),
        LocalModelSpec(
            id = "llm-qwen3.5-4b-q4km",
            kind = LocalModelKind.LOCAL_LLM,
            displayName = "Qwen 3.5 4B (Q4_K_M)",
            fileName = "Qwen3.5-4B-Q4_K_M.gguf",
            approxSizeMb = 2740,
            minRamGb = 8,
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/e87f176479d0855a907a41277aca2f8ee7a09523/Qwen3.5-4B-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF",
            summaryDe = "Maximale Offline-Intelligenz für Flaggschiff-Smartphones (8+ GB RAM) – professionelle E-Mails, komplexe Texte & perfekte Übersetzungen.",
            summaryEn = "Maximum offline intelligence for flagship phones (8+ GB RAM) — executive rewrites, complex cleanup & top translations.",
            speedBadgeDe = "~2,0–3,5 Sek.",
            speedBadgeEn = "~2.0–3.5s",
            highlightTagDe = "Max. Qualität · 8+ GB RAM",
            highlightTagEn = "Max Quality · 8+ GB RAM",
            supportsMultilingualCodeSwitching = true,
            supportsNoThinkTag = true,
            isFlagshipTier = true,
            shortEngineLabel = "Qwen 3.5 4B",
            exactSizeBytes = 2_740_937_888L,
            sha256 = "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4",
            licenseName = "Apache-2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            attributionNotice = "Alibaba Qwen Team / Unsloth GGUF",
            commercialUseNote = "Permitted (Apache-2.0)"
        )
    )

    val speechModels: List<LocalModelSpec> = sherpaModels + whisperModels

    val allModels: List<LocalModelSpec> = speechModels + llmModels

    fun findById(id: String): LocalModelSpec? = allModels.firstOrNull { it.id == id }

    fun recommendedWhisper(): LocalModelSpec = whisperModels.first { it.isRecommended }

    fun recommendedSherpa(): LocalModelSpec = sherpaModels.first { it.isRecommended }

    fun recommendedLlm(): LocalModelSpec = llmModels.first { it.isRecommended }

    fun recommendedForLocale(locale: String, multilingual: Boolean = false): LocalModelSpec {
        val base = KeyboardLocales.baseLang(locale)
        if (multilingual) {
            return when {
                base in NEMOTRON_MULTILINGUAL_LANGS ->
                    findById("sherpa-nemotron-3.5-streaming-multilingual-int8") ?: recommendedSherpa()
                base in EUROPEAN_25_LANGS ->
                    findById("sherpa-parakeet-ultra-0.6b-int8") ?: recommendedSherpa()
                else -> recommendedWhisper()
            }
        }
        return when {
            base == "de" -> findById("sherpa-fastconformer-de-pc-int8") ?: recommendedSherpa()
            base in setOf("en", "fr", "es") -> findById("sherpa-canary-180m-flash-int8") ?: recommendedSherpa()
            base in EUROPEAN_25_LANGS -> findById("sherpa-parakeet-ultra-0.6b-int8") ?: recommendedSherpa()
            else -> recommendedWhisper()
        }
    }
}
