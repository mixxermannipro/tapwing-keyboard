package com.tapwing.keyboard.ai

import com.tapwing.keyboard.data.KeyboardLocales

enum class LocalModelKind {
    WHISPER_STT,
    SHERPA_STREAMING,
    SHERPA_TRANSDUCER,
    SHERPA_CANARY;

    val isSherpa: Boolean
        get() = this != WHISPER_STT

    val isSpeechModel: Boolean
        get() = true
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
            summaryDe = "6–10× schneller als Whisper mit eingebauten Satzzeichen & Groß-/Kleinschreibung. Ideal für Deutsch.",
            summaryEn = "6–10x faster than Whisper with built-in punctuation & capitalization. Best speed/accuracy for German.",
            requiredFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
            supportedLanguages = setOf("de"),
            speedBadgeDe = "⚡ ~0,25 Sek.",
            speedBadgeEn = "⚡ ~0.25s",
            highlightTagDe = "Empfohlen · Deutsch",
            highlightTagEn = "Recommended · German",
            nativePunctuation = true,
            supportsStreaming = false,
            shortEngineLabel = "FastConformer"
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
            summaryDe = "Optimierte Weiterentwicklung von NVIDIA Parakeet v3 mit geringster Fehlerrate für Deutsch, Französisch & Spanisch (25 EU-Sprachen).",
            summaryEn = "Moondream fine-tune of NVIDIA Parakeet v3 with lowest WER in German, French & Spanish across 25 EU languages.",
            requiredFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
            supportedLanguages = EUROPEAN_25_LANGS,
            speedBadgeDe = "⚡ ~0,35 Sek.",
            speedBadgeEn = "⚡ ~0.35s",
            highlightTagDe = "Beste Qualität · 25 Sprachen",
            highlightTagEn = "Best accuracy · 25 languages",
            nativePunctuation = true,
            supportsStreaming = false,
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
            summaryDe = "Offizielles NVIDIA-Originalmodell für 25 europäische Sprachen mit eingebauten Satzzeichen & schnellem TDT-Decoder.",
            summaryEn = "Official NVIDIA checkpoint for 25 European languages with native punctuation and fast TDT decoding.",
            requiredFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
            supportedLanguages = EUROPEAN_25_LANGS,
            speedBadgeDe = "⚡ ~0,35 Sek.",
            speedBadgeEn = "⚡ ~0.35s",
            highlightTagDe = "NVIDIA Original · 25 Sprachen",
            highlightTagEn = "Official NVIDIA · 25 languages",
            nativePunctuation = true,
            supportsStreaming = false,
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
            summaryDe = "Schnell und kompakt für alle Sprachen oder ältere Geräte.",
            summaryEn = "Fast and lightweight for all languages or older phones.",
            speedBadgeDe = "~0,8–1,5 Sek.",
            speedBadgeEn = "~0.8–1.5s",
            highlightTagDe = "99 Sprachen · 60 MB",
            highlightTagEn = "99 languages · 60 MB",
            nativePunctuation = true,
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
            summaryDe = "Mehr Reserven für Namen und seltene Sprachen. Langsamer als Sherpa-Modelle.",
            summaryEn = "More capacity for names and 99 languages. Slower than Sherpa models.",
            speedBadgeDe = "~1,5–3,0 Sek.",
            speedBadgeEn = "~1.5–3.0s",
            highlightTagDe = "99 Sprachen · 190 MB",
            highlightTagEn = "99 languages · 190 MB",
            nativePunctuation = true,
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
            summaryDe = "Maximale Whisper-Genauigkeit für 99 Sprachen (nur für Geräte mit 8+ GB RAM).",
            summaryEn = "Larger 99-language speech model for flagship phones (8+ GB RAM).",
            speedBadgeDe = "~2,5–5,0 Sek.",
            speedBadgeEn = "~2.5–5.0s",
            highlightTagDe = "Flaggschiff · 574 MB",
            highlightTagEn = "Flagship · 574 MB",
            nativePunctuation = true,
            shortEngineLabel = "Whisper"
        )
    )

    val allModels: List<LocalModelSpec> = sherpaModels + whisperModels

    fun findById(id: String): LocalModelSpec? = allModels.firstOrNull { it.id == id }

    fun recommendedWhisper(): LocalModelSpec = whisperModels.first { it.isRecommended }

    fun recommendedSherpa(): LocalModelSpec = sherpaModels.first { it.isRecommended }

    fun recommendedForLocale(locale: String): LocalModelSpec {
        val base = KeyboardLocales.baseLang(locale)
        return when {
            base == "de" -> findById("sherpa-fastconformer-de-pc-int8")!!
            base in setOf("en", "fr", "es") -> findById("sherpa-canary-180m-flash-int8")!!
            base in EUROPEAN_25_LANGS -> findById("sherpa-parakeet-ultra-0.6b-int8")!!
            else -> recommendedWhisper()
        }
    }
}
