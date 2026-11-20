package com.tapwing.keyboard.ai

enum class LocalModelKind {
    WHISPER_STT,
    CLEANUP_LLM
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
    val supportsNoThink: Boolean = false,
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

    val llmModels: List<LocalModelSpec> = listOf(
        LocalModelSpec(
            id = "qwen3-1.7b-q4_k_m",
            kind = LocalModelKind.CLEANUP_LLM,
            displayName = "Qwen3 1.7B (Q4_K_M)",
            fileName = "Qwen3-1.7B-Q4_K_M.gguf",
            approxSizeMb = 1107,
            minRamGb = 6,
            downloadUrl = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF",
            isRecommended = true,
            supportsNoThink = true,
            summaryDe = "Für längere Texte und mehrsprachige Schreibhilfe. Braucht mehr Speicher als Qwen3 0.6B.",
            summaryEn = "For longer text and multilingual editing. Uses more memory than Qwen3 0.6B."
        ),
        LocalModelSpec(
            id = "qwen3-0.6b-q4_k_m",
            kind = LocalModelKind.CLEANUP_LLM,
            displayName = "Qwen3 0.6B (Q4_K_M)",
            fileName = "Qwen3-0.6B-Q4_K_M.gguf",
            approxSizeMb = 397,
            minRamGb = 4,
            downloadUrl = "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF",
            supportsNoThink = true,
            summaryDe = "Sehr schnell und sparsam (~400 MB) — ideal für Mittelklasse-Smartphones.",
            summaryEn = "Very fast and memory-light (~400 MB) — ideal for mid-range phones."
        ),
        LocalModelSpec(
            id = "gemma-3-1b-it-q4_k_m",
            kind = LocalModelKind.CLEANUP_LLM,
            displayName = "Gemma 3 1B-IT (Q4_K_M)",
            fileName = "gemma-3-1b-it-Q4_K_M.gguf",
            approxSizeMb = 806,
            minRamGb = 4,
            downloadUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF",
            summaryDe = "Googles kompaktes 1B-Modell für flüssigen Stil, Tonfall & Zeichensetzung.",
            summaryEn = "Google's compact 1B instruction model for smooth tone and punctuation."
        ),
        LocalModelSpec(
            id = "llama-3.2-1b-it-q4_k_m",
            kind = LocalModelKind.CLEANUP_LLM,
            displayName = "Llama 3.2 1B Instruct (Q4_K_M)",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            approxSizeMb = 808,
            minRamGb = 4,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            repoUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF",
            summaryDe = "Schnelles Meta-Modell mit sehr guter Geschwindigkeit auf Mobilgeräten.",
            summaryEn = "Fast Meta mobile instruction model for quick dictation cleanup."
        )
    )

    val allModels: List<LocalModelSpec> = whisperModels + llmModels

    fun findById(id: String): LocalModelSpec? = allModels.firstOrNull { it.id == id }

    fun recommendedWhisper(): LocalModelSpec = whisperModels.first { it.isRecommended }

    fun recommendedLlm(): LocalModelSpec = llmModels.first { it.isRecommended }
}
