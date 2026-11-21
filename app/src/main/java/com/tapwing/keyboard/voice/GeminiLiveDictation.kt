package com.tapwing.keyboard.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

internal data class GeminiLiveConfig(
    val apiKey: String,
    val smartFormatting: Boolean,
    val languageCode: String?
) {
    // Avoid accidentally exposing the user's key in a debug string.
    override fun toString(): String = "GeminiLiveConfig(smartFormatting=$smartFormatting, languageCode=$languageCode)"

    fun setup(): String {
        val transcription = JSONObject()
            .put("mode", if (smartFormatting) "SMART" else "VERBATIM")
            .put("languageCodes", JSONArray(listOfNotNull(normalizeLiveLanguage(languageCode))))
        return JSONObject().put("setup", JSONObject()
            .put("model", "models/${GeminiLiveDictation.MODEL}")
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray(listOf("TEXT"))))
            .put("inputAudioTranscription", transcription)
            .put("realtimeInputConfig", JSONObject()
                .put("automaticActivityDetection", JSONObject().put("disabled", true))))
            .toString()
    }
}

private fun normalizeLiveLanguage(tag: String?): String? = when (tag) {
    "de-CH" -> "de-DE"
    "es-ES" -> null // The Live model handles it through automatic detection.
    else -> tag
}

/** Holds only speech from the current recording. SMART finals replace earlier text. */
internal class LiveTranscript(private val smart: Boolean) {
    var text: String = ""
        private set
    var hasFinal: Boolean = false
        private set

    fun addFinal(value: String) {
        hasFinal = true
        text = if (smart) value.trim() else join(value)
    }

    fun preview(value: String): String = if (smart) value.trim() else join(value)

    private fun join(value: String): String =
        listOf(text, value.trim()).filter { it.isNotBlank() }.joinToString(" ")
}

internal class GeminiLiveDictation(
    config: GeminiLiveConfig,
    private val onPreview: (String) -> Unit,
    private val onFailure: () -> Unit,
    socketFactory: WebSocket.Factory = client
) : AutoCloseable {
    val formatsText = config.smartFormatting
    private val outcome = CompletableDeferred<Result<String>>()
    private val bufferedAudio = ArrayDeque<String>()
    private val transcript = LiveTranscript(config.smartFormatting)
    private var ready = false
    private var ended = false
    private var closed = false

    private val socket = socketFactory.newWebSocket(
        Request.Builder().url(ENDPOINT).header("x-goog-api-key", config.apiKey).build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!webSocket.send(config.setup())) fail()
            }

            override fun onMessage(webSocket: WebSocket, text: String) = receive(webSocket, text)

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = receive(webSocket, bytes.utf8())

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                fail()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = fail()
        }
    )

    @Synchronized
    fun offerPcm(data: ByteArray, byteCount: Int) {
        if (ended || closed || outcome.isCompleted) return
        val pcm = if (byteCount == data.size) data else data.copyOf(byteCount)
        val audio = Base64.getEncoder().encodeToString(pcm)
        val message = JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject()
            .put("data", audio).put("mimeType", "audio/pcm;rate=16000"))).toString()
        if (ready) {
            if (!send(socket, message)) fail()
        } else if (bufferedAudio.size < MAX_BUFFERED_FRAMES) {
            bufferedAudio.addLast(message)
        } else {
            fail()
        }
    }

    suspend fun finish(): Result<String> {
        endInput()
        return withTimeoutOrNull(5_000L) { outcome.await() }
            ?: Result.failure(IllegalStateException("Gemini Live did not finalize"))
    }

    @Synchronized
    private fun endInput() {
        if (closed || ended) return
        ended = true
        if (ready && !send(socket, ACTIVITY_END)) fail()
    }

    @Synchronized
    private fun receive(webSocket: WebSocket, message: String) {
        if (closed || outcome.isCompleted) return
        val root = runCatching { JSONObject(message) }.getOrNull() ?: return
        if (root.has("setupComplete") && !ready) {
            ready = true
            if (!send(webSocket, ACTIVITY_START)) {
                fail()
                return
            }
            while (bufferedAudio.isNotEmpty()) {
                if (!send(webSocket, bufferedAudio.removeFirst())) {
                    fail()
                    return
                }
            }
            if (ended && !send(webSocket, ACTIVITY_END)) fail()
            return
        }
        if (root.has("error")) {
            fail()
            return
        }
        val content = root.optJSONObject("serverContent") ?: return
        content.optJSONObject("interimInputTranscription")?.let {
            onPreview(transcript.preview((it.opt("text") as? String).orEmpty()))
        }
        content.optJSONObject("inputTranscription")?.let {
            transcript.addFinal((it.opt("text") as? String).orEmpty())
            onPreview(transcript.text)
        }
        if (ended && (content.optBoolean("turnComplete") ||
                (content.optBoolean("generationComplete") && transcript.hasFinal))) {
            outcome.complete(Result.success(transcript.text))
        }
    }

    private fun send(webSocket: WebSocket, message: String): Boolean =
        webSocket.queueSize() < MAX_OUTBOUND_BYTES && webSocket.send(message)

    @Synchronized
    private fun fail() {
        if (!closed && outcome.complete(Result.failure(IllegalStateException("Gemini Live unavailable")))) {
            bufferedAudio.clear()
            onFailure()
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        bufferedAudio.clear()
        outcome.cancel()
        socket.cancel()
    }

    companion object {
        const val MODEL = "gemini-3.5-transcribe-live"
        private const val ENDPOINT = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val ACTIVITY_START = """{"realtimeInput":{"activityStart":{}}}"""
        private const val ACTIVITY_END = """{"realtimeInput":{"activityEnd":{}}}"""
        private const val MAX_BUFFERED_FRAMES = 72 // Six seconds at the recorder's 83 ms cadence.
        private const val MAX_OUTBOUND_BYTES = 384 * 1024L
        private val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }
}
