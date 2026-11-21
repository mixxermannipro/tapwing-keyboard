package com.tapwing.keyboard.ai

enum class LocalModelKind {
    WHISPER_STT
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
    val summaryEn: String
)

object LocalModelCatalog {

    val whisperModels: List<LocalModelSpec> = listOf(
        LocalModelSpec(
            id = "whisper-small-q5_1",
            kind = LocalModelKind.WHISPER_STT,
            displayName = "Whisper Small (Q5_1)",
            fileName = "ggml-small-q5_1.bin",
            approxSizeMb = 190,
            minRamGb = 4,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            repoUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            isRecommended = true,
            summaryDe = "Mehr Reserven für Namen und Fachwörter. Braucht mehr Zeit und Speicher als Whisper Base.",
            summaryEn = "More capacity for names and technical terms. Takes more time and memory than Whisper Base."
        ),
        LocalModelSpec(
            id = "whisper-base-q5_1",
            kind = LocalModelKind.WHISPER_STT,
            displayName = "Whisper Base (Q5_1)",
            fileName = "ggml-base-q5_1.bin",
            approxSizeMb = 60,
            minRamGb = 3,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
            repoUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            summaryDe = "Schnell und kompakt für ältere Geräte oder kurze Notizen.",
            summaryEn = "Fast and lightweight for older phones or quick notes."
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
            summaryDe = "Minimaler Speicherbedarf (~32 MB), sehr schnell.",
            summaryEn = "Ultra-compact (~32 MB), fastest transcription."
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
            summaryDe = "Maximale Genauigkeit auch bei Fachbegriffen und Dialekten (für Smartphones mit 8+ GB RAM).",
            summaryEn = "Larger speech model for accents and terminology (8+ GB RAM phones)."
        )
    )

    val allModels: List<LocalModelSpec> = whisperModels

    fun findById(id: String): LocalModelSpec? = allModels.firstOrNull { it.id == id }

    fun recommendedWhisper(): LocalModelSpec = whisperModels.first { it.isRecommended }
}
