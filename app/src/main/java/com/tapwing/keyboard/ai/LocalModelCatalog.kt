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
    val shortEngineLabel: String = "Whisper"
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
            shortEngineLabel = "FastConformer"
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
            shortEngineLabel = "Nemotron Live"
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
            shortEngineLabel = "Live"
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
            shortEngineLabel = "Parakeet"
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
            shortEngineLabel = "Parakeet v3"
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
            shortEngineLabel = "Parakeet v2"
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
            shortEngineLabel = "Canary"
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
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
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
            shortEngineLabel = "Whisper"
        ),
        LocalModelSpec(
            id = "whisper-tiny-q5_1",
            kind = LocalModelKind.WHISPER_STT,
            displayName = "Whisper Tiny (Q5_1)",
            fileName = "ggml-tiny-q5_1.bin",
            approxSizeMb = 32,
            minRamGb = 2,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin",
            repoUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            summaryDe = "Minimaler Speicherbedarf (~32 MB), schnelle Fallback-Option für alle Sprachen.",
            summaryEn = "Ultra-compact (~32 MB), fast universal fallback.",
            speedBadgeDe = "~0,5–1,0 Sek.",
            speedBadgeEn = "~0.5–1.0s",
            highlightTagDe = "Ultra-Kompakt · 32 MB",
            highlightTagEn = "Ultra-compact · 32 MB",
            nativePunctuation = true,
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "Whisper"
        ),
        LocalModelSpec(
            id = "whisper-small-q5_1",
            kind = LocalModelKind.WHISPER_STT,
            displayName = "Whisper Small (Q5_1)",
            fileName = "ggml-small-q5_1.bin",
            approxSizeMb = 190,
            minRamGb = 4,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            repoUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            summaryDe = "Stark bei Fachbegriffen, Namen & mehrsprachigem Diktat (DE+EN gemischt).",
            summaryEn = "Strong for names, technical terms & bilingual dictation across 99 languages.",
            speedBadgeDe = "~1,5–3,0 Sek.",
            speedBadgeEn = "~1.5–3.0s",
            highlightTagDe = "99 Sprachen · 190 MB",
            highlightTagEn = "99 languages · 190 MB",
            nativePunctuation = true,
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "Whisper"
        ),
        LocalModelSpec(
            id = "whisper-large-v3-turbo-q5_0",
            kind = LocalModelKind.WHISPER_STT,
            displayName = "Whisper Large-v3 Turbo (Q5_0)",
            fileName = "ggml-large-v3-turbo-q5_0.bin",
            approxSizeMb = 574,
            minRamGb = 8,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
            repoUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            summaryDe = "Maximale Whisper-Genauigkeit für 99 Sprachen & Mischsprache (Geräte mit 8+ GB RAM).",
            summaryEn = "Maximum Whisper accuracy across 99 languages & code-switching (8+ GB RAM).",
            speedBadgeDe = "~2,5–5,0 Sek.",
            speedBadgeEn = "~2.5–5.0s",
            highlightTagDe = "Flaggschiff · 574 MB",
            highlightTagEn = "Flagship · 574 MB",
            nativePunctuation = true,
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "Whisper"
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
            downloadUrl = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF",
            isRecommended = true,
            summaryDe = "Hybrid-Architektur (Conv+Attention) speziell für Smartphones: extrem schnell (~40–60 tok/s) mit erstklassigem Deutsch & Englisch für Textbereinigung & Schreibhilfe.",
            summaryEn = "Hybrid mobile architecture (~40–60 tok/s on phone CPUs) with crisp German & English rewriting, proofreading, and dictation cleanup.",
            speedBadgeDe = "⚡ ~0,6–1,2 Sek.",
            speedBadgeEn = "⚡ ~0.6–1.2s",
            highlightTagDe = "Empfohlen · Ultra-Schnell",
            highlightTagEn = "Recommended · Ultra-Fast",
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "LFM2.5 1.2B"
        ),
        LocalModelSpec(
            id = "llm-qwen3.5-0.8b-q4km",
            kind = LocalModelKind.LOCAL_LLM,
            displayName = "Qwen 3.5 0.8B (Q4_K_M)",
            fileName = "Qwen3.5-0.8B-Q4_K_M.gguf",
            approxSizeMb = 533,
            minRamGb = 3,
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF",
            summaryDe = "Kompaktes 2026-Modell (533 MB) für jedes Smartphone. Blitzschnell im Direktmodus für Korrektur, Stichpunkte & Diktat-Glättung in 200+ Sprachen.",
            summaryEn = "Compact 2026 model (533 MB) for any phone. Blazing fast non-thinking cleanup, bullet lists & rewrites in 200+ languages.",
            speedBadgeDe = "⚡ ~0,4–0,9 Sek.",
            speedBadgeEn = "⚡ ~0.4–0.9s",
            highlightTagDe = "Kompakt · 533 MB",
            highlightTagEn = "Compact · 533 MB",
            supportsMultilingualCodeSwitching = true,
            supportsNoThinkTag = true,
            shortEngineLabel = "Qwen 3.5 0.8B"
        ),
        LocalModelSpec(
            id = "llm-qwen3.5-2b-q4km",
            kind = LocalModelKind.LOCAL_LLM,
            displayName = "Qwen 3.5 2B (Q4_K_M)",
            fileName = "Qwen3.5-2B-Q4_K_M.gguf",
            approxSizeMb = 1280,
            minRamGb = 6,
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF",
            summaryDe = "Cloud-Niveau direkt auf dem Handy: überragende Grammatik, Tonfall-Wechsel, Stichpunkte & DE+EN Mischsprache (Apache-2.0).",
            summaryEn = "Cloud-grade quality on device: outstanding grammar, tone shifts, bullet lists & multilingual code-switching (Apache-2.0).",
            speedBadgeDe = "⚡ ~1,0–2,0 Sek.",
            speedBadgeEn = "⚡ ~1.0–2.0s",
            highlightTagDe = "Beste Balance · Cloud-Niveau",
            highlightTagEn = "Best Balance · Cloud-Grade",
            supportsMultilingualCodeSwitching = true,
            supportsNoThinkTag = true,
            shortEngineLabel = "Qwen 3.5 2B"
        ),
        LocalModelSpec(
            id = "llm-gemma3-1b-q4km",
            kind = LocalModelKind.LOCAL_LLM,
            displayName = "Google Gemma 3 1B IT (Q4_K_M)",
            fileName = "gemma-3-1b-it-Q4_K_M.gguf",
            approxSizeMb = 806,
            minRamGb = 4,
            downloadUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF",
            summaryDe = "Leichtes Google-Modell mit sehr natürlichem Stil beim Umformulieren, Korrigieren & Zusammenfassen (140+ Sprachen).",
            summaryEn = "Lightweight Google model delivering natural phrasing for proofreading, rephrasing & summarizing across 140+ languages.",
            speedBadgeDe = "⚡ ~0,6–1,3 Sek.",
            speedBadgeEn = "⚡ ~0.6–1.3s",
            highlightTagDe = "Google · 806 MB",
            highlightTagEn = "Google · 806 MB",
            supportsMultilingualCodeSwitching = true,
            shortEngineLabel = "Gemma 3 1B"
        ),
        LocalModelSpec(
            id = "llm-lfm2.5-2.6b-q4km",
            kind = LocalModelKind.LOCAL_LLM,
            displayName = "LiquidAI LFM2.5 2.6B (Q4_K_M)",
            fileName = "LFM2.5-2.6B-Q4_K_M.gguf",
            approxSizeMb = 1670,
            minRamGb = 6,
            downloadUrl = "https://huggingface.co/LiquidAI/LFM2.5-2.6B-GGUF/resolve/main/LFM2.5-2.6B-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/LiquidAI/LFM2.5-2.6B-GGUF",
            summaryDe = "Flaggschiff-Hybridmodell (Aug. 2026): spielt auf dem Niveau großer Cloud-Modelle bei ~30 tok/s auf modernen Smartphones.",
            summaryEn = "Flagship 2026 hybrid model rivaling much larger cloud models at ~30 tok/s on modern smartphone CPUs.",
            speedBadgeDe = "⚡ ~1,2–2,2 Sek.",
            speedBadgeEn = "⚡ ~1.2–2.2s",
            highlightTagDe = "Flaggschiff · Schnell",
            highlightTagEn = "Flagship · Fast",
            supportsMultilingualCodeSwitching = true,
            isFlagshipTier = true,
            shortEngineLabel = "LFM2.5 2.6B"
        ),
        LocalModelSpec(
            id = "llm-qwen3.5-4b-q4km",
            kind = LocalModelKind.LOCAL_LLM,
            displayName = "Qwen 3.5 4B (Q4_K_M)",
            fileName = "Qwen3.5-4B-Q4_K_M.gguf",
            approxSizeMb = 2740,
            minRamGb = 8,
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
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
            shortEngineLabel = "Qwen 3.5 4B"
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
