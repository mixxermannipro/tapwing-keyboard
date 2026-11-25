package com.tapwing.keyboard.ai

import android.content.Context
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest
import com.tapwing.keyboard.data.KeyboardLocales
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class NanoAvailability {
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE
}

/**
 * Defensive wrapper around Google ML Kit GenAI Proofreading & Rewriting (backed by Gemini Nano / AICore).
 * On devices with AICore (Pixel, supported Galaxy/flagships), runs fast on-device cleanup & rewrites.
 * On unsupported devices, returns UNAVAILABLE/failure instantaneously so fallback runs with zero lag.
 */
object GeminiNanoClient {

    private const val CHECK_TIMEOUT_MS = 2_500L
    private const val INFER_TIMEOUT_MS = 8_000L

    suspend fun checkAvailability(context: Context, locale: String): NanoAvailability =
        withContext(Dispatchers.IO) {
            val lang = proofreaderLanguage(locale) ?: return@withContext NanoAvailability.UNAVAILABLE
            runCatching {
                val options = ProofreaderOptions.builder(context.applicationContext)
                    .setInputType(ProofreaderOptions.InputType.VOICE)
                    .setLanguage(lang)
                    .build()
                val client = Proofreading.getClient(options)
                try {
                    val status = withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                        client.checkFeatureStatus().await()
                    } ?: FeatureStatus.UNAVAILABLE
                    mapStatus(status)
                } finally {
                    runCatching { client.close() }
                }
            }.getOrDefault(NanoAvailability.UNAVAILABLE)
        }

    suspend fun requestDownload(context: Context, locale: String): Boolean =
        withContext(Dispatchers.IO) {
            val lang = proofreaderLanguage(locale) ?: return@withContext false
            runCatching {
                val options = ProofreaderOptions.builder(context.applicationContext)
                    .setInputType(ProofreaderOptions.InputType.VOICE)
                    .setLanguage(lang)
                    .build()
                val client = Proofreading.getClient(options)
                try {
                    val done = CompletableDeferred<Boolean>()
                    client.downloadFeature(object : DownloadCallback {
                        override fun onDownloadStarted(bytesToDownload: Long) {}
                        override fun onDownloadProgress(totalBytesDownloaded: Long) {}
                        override fun onDownloadCompleted() {
                            done.complete(true)
                        }
                        override fun onDownloadFailed(e: GenAiException) {
                            done.complete(false)
                        }
                    })
                    withTimeoutOrNull(60_000L) { done.await() } ?: false
                } finally {
                    runCatching { client.close() }
                }
            }.getOrDefault(false)
        }

    suspend fun processText(
        context: Context,
        text: String,
        action: WritingAction,
        locale: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success("")
        when (action) {
            WritingAction.VOICE_CLEANUP, WritingAction.PROOFREAD ->
                runProofread(context.applicationContext, text, action == WritingAction.VOICE_CLEANUP, locale)
            WritingAction.PROFESSIONAL, WritingAction.CASUAL, WritingAction.CONCISE,
            WritingAction.EMOTIVE, WritingAction.REPHRASE ->
                runRewrite(context.applicationContext, text, action, locale)
            WritingAction.SUMMARIZE, WritingAction.BULLET_LIST, WritingAction.TRANSLATE ->
                Result.failure(UnsupportedOperationException("Action handled by fallback"))
        }
    }

    private suspend fun runProofread(
        context: Context,
        text: String,
        fromVoice: Boolean,
        locale: String
    ): Result<String> = runCatching {
        val lang = proofreaderLanguage(locale) ?: error("Unsupported Gemini Nano language")
        val options = ProofreaderOptions.builder(context)
            .setInputType(if (fromVoice) ProofreaderOptions.InputType.VOICE else ProofreaderOptions.InputType.KEYBOARD)
            .setLanguage(lang)
            .build()
        val client = Proofreading.getClient(options)
        try {
            val status = withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                client.checkFeatureStatus().await()
            } ?: FeatureStatus.UNAVAILABLE
            check(status == FeatureStatus.AVAILABLE) { "Gemini Nano unavailable ($status)" }
            val result = withTimeoutOrNull(INFER_TIMEOUT_MS) {
                client.runInference(ProofreadingRequest.builder(text).build()).await()
            } ?: error("Gemini Nano timeout")
            val top = result.results.firstOrNull()?.text?.trim().orEmpty()
            check(top.isNotEmpty()) { "Empty Gemini Nano response" }
            top
        } finally {
            runCatching { client.close() }
        }
    }

    private suspend fun runRewrite(
        context: Context,
        text: String,
        action: WritingAction,
        locale: String
    ): Result<String> = runCatching {
        val lang = rewriterLanguage(locale) ?: error("Unsupported Gemini Nano language")
        val outputType = when (action) {
            WritingAction.PROFESSIONAL -> RewriterOptions.OutputType.PROFESSIONAL
            WritingAction.CASUAL -> RewriterOptions.OutputType.FRIENDLY
            WritingAction.CONCISE -> RewriterOptions.OutputType.SHORTEN
            WritingAction.EMOTIVE -> RewriterOptions.OutputType.EMOJIFY
            WritingAction.REPHRASE -> RewriterOptions.OutputType.REPHRASE
            else -> error("Unsupported rewrite type")
        }
        val options = RewriterOptions.builder(context)
            .setLanguage(lang)
            .setOutputType(outputType)
            .build()
        val client = Rewriting.getClient(options)
        try {
            val status = withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                client.checkFeatureStatus().await()
            } ?: FeatureStatus.UNAVAILABLE
            check(status == FeatureStatus.AVAILABLE) { "Gemini Nano unavailable ($status)" }
            val result = withTimeoutOrNull(INFER_TIMEOUT_MS) {
                client.runInference(RewritingRequest.builder(text).build()).await()
            } ?: error("Gemini Nano timeout")
            val top = result.results.firstOrNull()?.text?.trim().orEmpty()
            check(top.isNotEmpty()) { "Empty Gemini Nano response" }
            top
        } finally {
            runCatching { client.close() }
        }
    }

    private fun mapStatus(status: Int): NanoAvailability = when (status) {
        FeatureStatus.AVAILABLE -> NanoAvailability.AVAILABLE
        FeatureStatus.DOWNLOADABLE -> NanoAvailability.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> NanoAvailability.DOWNLOADING
        else -> NanoAvailability.UNAVAILABLE
    }

    private fun proofreaderLanguage(locale: String): Int? = when (KeyboardLocales.baseLang(locale)) {
        "en" -> ProofreaderOptions.Language.ENGLISH
        "de" -> ProofreaderOptions.Language.GERMAN
        "fr" -> ProofreaderOptions.Language.FRENCH
        "it" -> ProofreaderOptions.Language.ITALIAN
        "es" -> ProofreaderOptions.Language.SPANISH
        else -> null
    }

    private fun rewriterLanguage(locale: String): Int? = when (KeyboardLocales.baseLang(locale)) {
        "en" -> RewriterOptions.Language.ENGLISH
        "de" -> RewriterOptions.Language.GERMAN
        "fr" -> RewriterOptions.Language.FRENCH
        "it" -> RewriterOptions.Language.ITALIAN
        "es" -> RewriterOptions.Language.SPANISH
        else -> null
    }
}
