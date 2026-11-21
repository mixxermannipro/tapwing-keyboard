package com.tapwing.keyboard.ai

import com.tapwing.keyboard.data.AiProviderType

data class AiModelPreset(
    val id: String,
    val label: String,
    val isFreeTier: Boolean,
    val supportsAudio: Boolean,
    val supportsText: Boolean,
    val noteDe: String,
    val noteEn: String
)

/**
 * Curated catalog of verified free-tier & low-latency models per provider,
 * while always allowing users to type any custom model ID.
 */
object AiModelCatalog {

    val allModels: Map<AiProviderType, List<AiModelPreset>> = mapOf(
        AiProviderType.GEMINI_API to listOf(
            AiModelPreset(
                id = "gemini-3.8-flash",
                label = "Gemini 3.8 Flash",
                isFreeTier = true,
                supportsAudio = true,
                supportsText = true,
                noteDe = "Bestes Free-Tier-Modell (Qualität & Tempo)",
                noteEn = "Best Free-Tier model (quality & speed)"
            ),
            AiModelPreset(
                id = "gemini-3.5-flash-lite",
                label = "Gemini 3.5 Flash-Lite",
                isFreeTier = true,
                supportsAudio = true,
                supportsText = true,
                noteDe = "Free Tier · extrem schnell für Diktat & Bereinigung",
                noteEn = "Free tier · ultra-fast for dictation & cleanup"
            ),
            AiModelPreset(
                id = "gemini-2.5-flash",
                label = "Gemini 2.5 Flash",
                isFreeTier = true,
                supportsAudio = true,
                supportsText = true,
                noteDe = "Free Tier · stabil & präzise",
                noteEn = "Free tier · stable & accurate"
            ),
            AiModelPreset(
                id = "gemini-2.5-flash-lite",
                label = "Gemini 2.5 Flash-Lite",
                isFreeTier = true,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Free Tier · geringste Latenz",
                noteEn = "Free tier · lowest latency"
            ),
            AiModelPreset(
                id = "gemma-4",
                label = "Gemma 4",
                isFreeTier = true,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Free Tier · Open Weights von Google",
                noteEn = "Free tier · open-weight Google model"
            )
        ),
        AiProviderType.GROQ_API to listOf(
            AiModelPreset(
                id = "whisper-large-v3-turbo",
                label = "Whisper Large v3 Turbo",
                isFreeTier = true,
                supportsAudio = true,
                supportsText = false,
                noteDe = "Free Tier · bestes schnelles Diktat (~0,2 s)",
                noteEn = "Free tier · best fast dictation (~0.2s)"
            ),
            AiModelPreset(
                id = "whisper-large-v3",
                label = "Whisper Large v3",
                isFreeTier = true,
                supportsAudio = true,
                supportsText = false,
                noteDe = "Free Tier · maximale Erkennungsgenauigkeit",
                noteEn = "Free tier · maximum speech accuracy"
            ),
            AiModelPreset(
                id = "llama-3.1-8b-instant",
                label = "Llama 3.1 8B Instant",
                isFreeTier = true,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Free Tier · sofortige Bereinigung",
                noteEn = "Free tier · instant cleanup"
            ),
            AiModelPreset(
                id = "llama-3.3-70b-versatile",
                label = "Llama 3.3 70B Versatile",
                isFreeTier = true,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Free Tier · beste Textqualität auf Groq",
                noteEn = "Free tier · best writing quality on Groq"
            ),
            AiModelPreset(
                id = "openai/gpt-oss-120b",
                label = "GPT-OSS 120B",
                isFreeTier = true,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Free Tier · Open-Weight Flagship (~500 tok/s)",
                noteEn = "Free tier · open-weight flagship (~500 tok/s)"
            ),
            AiModelPreset(
                id = "openai/gpt-oss-20b",
                label = "GPT-OSS 20B",
                isFreeTier = true,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Free Tier · extrem schnell (~1000 tok/s)",
                noteEn = "Free tier · ultra-fast (~1000 tok/s)"
            ),
            AiModelPreset(
                id = "qwen/qwen3.8-27b",
                label = "Qwen 3.8 27B",
                isFreeTier = true,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Free Tier · stark bei Deutsch & Mehrsprachigkeit",
                noteEn = "Free tier · strong multilingual & German"
            )
        ),
        AiProviderType.OPENROUTER_API to listOf(
            AiModelPreset(
                id = "meta-llama/llama-3.3-70b-instruct:free",
                label = "Llama 3.3 70B (:free)",
                isFreeTier = true,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Kostenlos · Top Allround-Schreibhilfe",
                noteEn = "Free tier · top all-round writing model"
            ),
            AiModelPreset(
                id = "google/gemma-3-27b-it:free",
                label = "Gemma 3 27B (:free)",
                isFreeTier = true,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Kostenlos · stark auf Deutsch & Englisch",
                noteEn = "Free tier · strong German & English"
            ),
            AiModelPreset(
                id = "qwen/qwen3-32b:free",
                label = "Qwen 3 32B (:free)",
                isFreeTier = true,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Kostenlos · schnell & präzise",
                noteEn = "Free tier · fast & precise"
            ),
            AiModelPreset(
                id = "deepseek/deepseek-r1:free",
                label = "DeepSeek R1 (:free)",
                isFreeTier = true,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Kostenlos · tiefes Umformulieren",
                noteEn = "Free tier · deep reasoning & rewriting"
            )
        ),
        AiProviderType.XAI_API to listOf(
            AiModelPreset(
                id = "grok-3-mini",
                label = "Grok 3 Mini",
                isFreeTier = false,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Schnell & günstig bei xAI",
                noteEn = "Fast & affordable xAI model"
            ),
            AiModelPreset(
                id = "grok-3",
                label = "Grok 3",
                isFreeTier = false,
                supportsAudio = false,
                supportsText = true,
                noteDe = "xAI Flagship-Schreibmodell",
                noteEn = "xAI flagship writing model"
            ),
            AiModelPreset(
                id = "grok-2-latest",
                label = "Grok 2 Latest",
                isFreeTier = false,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Bewährtes xAI-Modell",
                noteEn = "Classic xAI model"
            )
        ),
        AiProviderType.OPENAI_COMPATIBLE to listOf(
            AiModelPreset(
                id = "gpt-4o-mini-transcribe",
                label = "GPT-4o Mini Transcribe",
                isFreeTier = false,
                supportsAudio = true,
                supportsText = false,
                noteDe = "OpenAI Audio-Transkription",
                noteEn = "OpenAI audio transcription"
            ),
            AiModelPreset(
                id = "whisper-1",
                label = "Whisper-1",
                isFreeTier = false,
                supportsAudio = true,
                supportsText = false,
                noteDe = "Klassisches OpenAI Whisper",
                noteEn = "Classic OpenAI Whisper"
            ),
            AiModelPreset(
                id = "gpt-4.1-mini",
                label = "GPT-4.1 Mini",
                isFreeTier = false,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Starkes OpenAI-Schreibmodell",
                noteEn = "Strong OpenAI writing model"
            ),
            AiModelPreset(
                id = "gpt-4.1-nano",
                label = "GPT-4.1 Nano",
                isFreeTier = false,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Ultra-schnell & sparsam",
                noteEn = "Ultra-fast & low cost"
            ),
            AiModelPreset(
                id = "gpt-4o-mini",
                label = "GPT-4o Mini",
                isFreeTier = false,
                supportsAudio = false,
                supportsText = true,
                noteDe = "Bewährtes Allround-Modell",
                noteEn = "Proven all-round model"
            )
        )
    )

    fun textModelsFor(provider: AiProviderType): List<AiModelPreset> =
        allModels[provider].orEmpty().filter { it.supportsText }

    fun audioModelsFor(provider: AiProviderType): List<AiModelPreset> =
        allModels[provider].orEmpty().filter { it.supportsAudio }

    fun defaultTextModelFor(provider: AiProviderType): String = when (provider) {
        AiProviderType.GEMINI_API -> "gemini-3.8-flash"
        AiProviderType.GROQ_API -> "llama-3.3-70b-versatile"
        AiProviderType.OPENROUTER_API -> "meta-llama/llama-3.3-70b-instruct:free"
        AiProviderType.XAI_API -> "grok-3-mini"
        AiProviderType.OPENAI_COMPATIBLE -> "gpt-4.1-mini"
        AiProviderType.GEMINI_NANO, AiProviderType.LOCAL_RULES -> ""
    }

    fun defaultAudioModelFor(provider: AiProviderType): String = when (provider) {
        AiProviderType.GEMINI_API -> "gemini-3.5-flash-lite"
        AiProviderType.GROQ_API -> "whisper-large-v3-turbo"
        AiProviderType.OPENAI_COMPATIBLE -> "gpt-4o-mini-transcribe"
        else -> ""
    }

    fun displayName(provider: AiProviderType, de: Boolean): String = when (provider) {
        AiProviderType.LOCAL_RULES -> if (de) "On-Device Regeln (Offline)" else "On-Device Rules (Offline)"
        AiProviderType.GEMINI_NANO -> "Gemini Nano (AICore)"
        AiProviderType.GEMINI_API -> "Google Gemini"
        AiProviderType.GROQ_API -> "Groq (LPU)"
        AiProviderType.OPENROUTER_API -> "OpenRouter"
        AiProviderType.XAI_API -> "xAI Grok"
        AiProviderType.OPENAI_COMPATIBLE -> if (de) "OpenAI / Eigener Endpunkt" else "OpenAI / Custom"
    }

    fun signupUrl(provider: AiProviderType): String = when (provider) {
        AiProviderType.GEMINI_API -> "https://aistudio.google.com/apikey"
        AiProviderType.GROQ_API -> "https://console.groq.com/keys"
        AiProviderType.OPENROUTER_API -> "https://openrouter.ai/keys"
        AiProviderType.XAI_API -> "https://console.x.ai"
        AiProviderType.OPENAI_COMPATIBLE -> "https://platform.openai.com/api-keys"
        else -> ""
    }

    fun freeTierSummary(provider: AiProviderType, de: Boolean): String = when (provider) {
        AiProviderType.GEMINI_API -> if (de) {
            "Kostenlos ohne Kreditkarte (gemini-3.8-flash, gemini-3.5-flash-lite, gemma-4)."
        } else {
            "Free tier without credit card (gemini-3.8-flash, gemini-3.5-flash-lite, gemma-4)."
        }
        AiProviderType.GROQ_API -> if (de) {
            "Großzügiger Free Account ohne Karte: Whisper Turbo + Llama 3.3 70B + GPT-OSS 120B."
        } else {
            "Generous free tier without card: Whisper Turbo + Llama 3.3 70B + GPT-OSS 120B."
        }
        AiProviderType.OPENROUTER_API -> if (de) {
            "Viele :free-Modelle (Llama 3.3 70B, Gemma 3, Qwen 3, DeepSeek R1) kostenlos nutzbar."
        } else {
            "Many :free models (Llama 3.3 70B, Gemma 3, Qwen 3, DeepSeek R1) at $0."
        }
        AiProviderType.XAI_API -> if (de) {
            "xAI Grok API (grok-3-mini, grok-3) über console.x.ai."
        } else {
            "xAI Grok API (grok-3-mini, grok-3) via console.x.ai."
        }
        AiProviderType.OPENAI_COMPATIBLE -> if (de) {
            "OpenAI oder beliebiger OpenAI-kompatibler Server (Ollama, Together, Mistral, LM Studio)."
        } else {
            "OpenAI or any OpenAI-compatible endpoint (Ollama, Together, Mistral, LM Studio)."
        }
        else -> ""
    }
}
