package com.tapwing.keyboard.voice

import com.tapwing.keyboard.data.SecureKeyStorage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveDictationTest {
    @Test
    fun sendsAudioDuringRecordingAndFinalizesWithoutAnotherUpload() = runTest {
        val server = FakeSocketFactory()
        val previews = mutableListOf<String>()
        GeminiLiveDictation(
            GeminiLiveConfig("fixture-key", smartFormatting = true, languageCode = "de-DE"),
            onPreview = { previews += it },
            onFailure = { error("Fixture failed") },
            socketFactory = server
        ).use { live ->
            assertEquals("fixture-key", server.request.header("x-goog-api-key"))
            assertFalse(server.request.url.toString().contains("fixture-key"))
            server.open()
            live.offerPcm(byteArrayOf(1, 2, 3, 4), 4)
            assertEquals(1, server.messages.size)
            server.reply("""{"setupComplete":{}}""")
            assertTrue(server.messages[1].contains("activityStart"))
            assertTrue(server.messages[2].contains("AQIDBA=="))
            server.reply("""{"serverContent":{"interimInputTranscription":{"text":"Hallo"}}}""")
            assertEquals(listOf("Hallo"), previews)

            val final = async(start = CoroutineStart.UNDISPATCHED) { live.finish() }
            assertTrue(server.messages.last().contains("activityEnd"))
            server.reply("""{"serverContent":{"inputTranscription":{"text":"Hallo Welt."},"turnComplete":true}}""")
            assertEquals("Hallo Welt.", final.await().getOrThrow())
        }
        assertTrue(server.cancelled)
    }

    @Test
    fun rejectsKeysThatCannotBeUsedInRequestHeaders() {
        assertTrue(SecureKeyStorage.isValidApiKey(" fixture-key "))
        assertFalse(SecureKeyStorage.isValidApiKey("fixture\nunexpected"))
        assertFalse(SecureKeyStorage.isValidApiKey("fixture–key"))
        assertFalse(SecureKeyStorage.isValidApiKey(" "))
    }

    @Test
    fun smartFinalReplacesEarlierTextAndRespectsAnEmptyEdit() {
        val transcript = LiveTranscript(smart = true)
        transcript.addFinal("Morgen um vier.")
        transcript.addFinal("Morgen um fünf.")
        assertEquals("Morgen um fünf.", transcript.text)
        transcript.addFinal("")
        assertEquals("", transcript.text)
    }

    @Test
    fun verbatimAppendsFinalsButKeepsProvisionalWordsOutOfTheResult() {
        val transcript = LiveTranscript(smart = false)
        transcript.addFinal("hello")
        assertEquals("hello wor", transcript.preview("wor"))
        assertEquals("hello", transcript.text)
        transcript.addFinal("world")
        assertEquals("hello world", transcript.text)
    }

    @Test
    fun configUsesManualStopsAndSendsNoSecretInTheSetupPayload() {
        val config = GeminiLiveConfig("fixture-key", smartFormatting = true, languageCode = "de-CH")
        val payload = JSONObject(config.setup()).getJSONObject("setup")
        assertEquals("models/gemini-3.5-transcribe-live", payload.getString("model"))
        assertEquals(true, payload.getJSONObject("realtimeInputConfig").getJSONObject("automaticActivityDetection").getBoolean("disabled"))
        assertEquals("SMART", payload.getJSONObject("inputAudioTranscription").getString("mode"))
        assertEquals("de-DE", payload.getJSONObject("inputAudioTranscription").getJSONArray("languageCodes").getString(0))
        assertFalse(config.setup().contains("fixture-key"))
        assertFalse(config.toString().contains("fixture-key"))
    }
    private class FakeSocketFactory : WebSocket.Factory {
        lateinit var request: Request
        private lateinit var listener: WebSocketListener
        private lateinit var socket: WebSocket
        val messages = mutableListOf<String>()
        var cancelled = false

        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
            this.request = request
            this.listener = listener
            socket = object : WebSocket {
                override fun request(): Request = request
                override fun queueSize(): Long = 0L
                override fun send(text: String): Boolean = messages.add(text)
                override fun send(bytes: ByteString): Boolean = send(bytes.utf8())
                override fun close(code: Int, reason: String?): Boolean = true
                override fun cancel() { cancelled = true }
            }
            return socket
        }

        fun open() {
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(101).message("Switching Protocols").build()
            listener.onOpen(socket, response)
        }

        fun reply(message: String) = listener.onMessage(socket, message)
    }
}
