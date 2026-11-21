package com.tapwing.keyboard.voice

import com.tapwing.keyboard.dictionary.SuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlin.system.measureNanoTime

class VoiceReliabilityAndBenchmarkTest {

    @Test
    fun wavChunkerSplitsFiveMinuteRecordingCleanlyWithValidHeaders() {
        val tmpDir = createTempDir("tapwing_wav_test")
        try {
            val seconds = 300 // 5 minutes of 16 kHz mono 16-bit PCM = 9,600,000 bytes
            val totalPcmBytes = seconds * WavHeaderUtil.PCM_BYTES_PER_SECOND
            val sourceWav = File(tmpDir, "long_dictation.wav")
            RandomAccessFile(sourceWav, "rw").use { raf ->
                raf.write(WavHeaderUtil.buildHeader(totalPcmBytes))
                raf.setLength(WavHeaderUtil.HEADER_BYTES + totalPcmBytes)
            }

            var chunks: List<File> = emptyList()
            val splitNs = measureNanoTime {
                chunks = WavChunker.splitIfNeeded(sourceWav, maxChunkSeconds = 45)
            }
            val splitMs = splitNs / 1_000_000.0
            println("BENCHMARK wav_chunker_5min_ms=%.2f chunks=%d".format(splitMs, chunks.size))

            assertEquals(7, chunks.size) // 300s / 45s = 6*45s + 30s
            val sumPcmBytes = chunks.sumOf { file ->
                assertTrue(file.length() > WavHeaderUtil.HEADER_BYTES)
                file.length() - WavHeaderUtil.HEADER_BYTES
            }
            assertEquals(totalPcmBytes, sumPcmBytes)
            chunks.filter { it != sourceWav }.forEach { it.delete() }
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun incrementalWhisperDeduplicatesOverlappingBoundaryWords() {
        val joined = IncrementalWhisper.joinTranscriptSegments(
            "Wir treffen uns morgen im",
            "morgen im Büro um fünfzehn Uhr."
        )
        assertEquals("Wir treffen uns morgen im Büro um fünfzehn Uhr.", joined)

        val prompt = IncrementalWhisper.buildContextPrompt("Tapwing, Pixel", "Hallo Welt")
        assertEquals("Tapwing, Pixel. Hallo Welt", prompt)
    }

    @Test
    fun benchmarkVoiceAndSuggestionPipelineLatency() {
        val samples = listOf(
            "ähm hallo tim komma wie geht es dir heute fragezeichen",
            "ich komme um acht uhr nein warte um neun uhr ins büro",
            "um let's meet on monday wait no tuesday at 3 PM actually make that 2 PM",
            "schick mir bitte 50 euro nein warte 65 euro per überweisung",
            "einkaufsliste doppelpunkt stichpunkt milch stichpunkt brot stichpunkt äpfel"
        )

        // Warmup JIT
        repeat(200) { idx ->
            NaturalVoiceCleaner.clean(samples[idx % samples.size], if (idx % 5 == 2) "en" else "de")
        }

        val iterations = 1000
        val latenciesNs = LongArray(iterations)
        var totalWords = 0
        val cleanerStart = System.nanoTime()
        for (i in 0 until iterations) {
            val s = samples[i % samples.size]
            val loc = if (i % 5 == 2) "en" else "de"
            val t0 = System.nanoTime()
            val out = NaturalVoiceCleaner.clean(s, loc)
            latenciesNs[i] = System.nanoTime() - t0
            totalWords += out.split(' ').size
        }
        val cleanerTotalMs = (System.nanoTime() - cleanerStart) / 1_000_000.0
        val sortedLat = latenciesNs.sortedArray()
        val avgCleanerMs = cleanerTotalMs / iterations
        val p95CleanerMs = sortedLat[(iterations * 0.95).toInt()] / 1_000_000.0
        val wordsPerSec = (totalWords / (cleanerTotalMs / 1000.0)).toLong()
        println(
            "BENCHMARK voice_cleaner_1000_total_ms=%.2f avg_ms=%.4f p95_ms=%.4f words_per_sec=%d"
                .format(cleanerTotalMs, avgCleanerMs, p95CleanerMs, wordsPerSec)
        )
        assertTrue(avgCleanerMs < 5.0)

        // 10-minute (600s) streaming VAD + chunk boundary benchmark at 83ms frame cadence
        val vad = AdaptiveSpeechDetector()
        val boundaries = SpeechChunkBoundaries()
        val frameBytes = 2_656L // ~83ms at 32,000 B/s
        val totalFrames = (600L * 32_000L / frameBytes).toInt()
        var chunkCount = 0
        val vadNs = measureNanoTime {
            var pos = 0L
            for (f in 0 until totalFrames) {
                pos += frameBytes
                val cycle = f % 260 // ~21.5s speech + pause cycle
                val rms = if (cycle < 245) 420.0 else 18.0
                val voiced = vad.observe(rms)
                if (boundaries.observe(pos, voiced = voiced, decoderReady = true) != null) {
                    chunkCount++
                }
            }
            if (boundaries.tail(pos) != null) chunkCount++
        }
        val vadMs = vadNs / 1_000_000.0
        val rtf = vadMs / 600_000.0
        println(
            "BENCHMARK vad_chunking_10min_ms=%.3f frames=%d chunks=%d rtf=%.7f"
                .format(vadMs, totalFrames, chunkCount, rtf)
        )
        assertTrue(chunkCount > 20)
        assertTrue(vadMs < 500.0)

        // SuggestionEngine query latency benchmark (500 lookups)
        val queries = listOf("Hallo ", "wie geh", "Ich bin ", "Thank ", "12*15=")
        repeat(50) { i -> SuggestionEngine.suggestionsFor(queries[i % queries.size], "de") }
        val suggCount = 500
        val suggNs = measureNanoTime {
            for (i in 0 until suggCount) {
                SuggestionEngine.suggestionsFor(queries[i % queries.size], if (i % 5 == 3) "en" else "de")
            }
        }
        val suggTotalMs = suggNs / 1_000_000.0
        val avgSuggMs = suggTotalMs / suggCount
        println("BENCHMARK suggestion_engine_500_total_ms=%.2f avg_ms=%.4f".format(suggTotalMs, avgSuggMs))
        assertTrue(avgSuggMs < 10.0)
    }
}
