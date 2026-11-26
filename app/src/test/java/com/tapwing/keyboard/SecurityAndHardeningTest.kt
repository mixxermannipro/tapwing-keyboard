package com.tapwing.keyboard

import android.Manifest
import com.tapwing.keyboard.ai.CloudAiClient
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.ai.TarBz2Extractor
import com.tapwing.keyboard.dictionary.CustomDictionaryParser
import com.tapwing.keyboard.dictionary.KeyCenter
import com.tapwing.keyboard.dictionary.SuggestionEngine
import com.tapwing.keyboard.dictionary.SwipeGestureDecoder
import com.tapwing.keyboard.dictionary.SwipePoint
import com.tapwing.keyboard.ime.PermissionBridgeActivity
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SecurityAndHardeningTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun cloudAiClientEnforcesHttpsAndLoopbackOnlyHttp() {
        assertTrue(CloudAiClient.isValidHttpUrl("https://api.openai.com/v1/chat/completions"))
        assertTrue(CloudAiClient.isValidHttpUrl("http://localhost:11434/v1/chat/completions"))
        assertTrue(CloudAiClient.isValidHttpUrl("http://127.0.0.1:8080/v1/chat/completions"))
        assertTrue(CloudAiClient.isValidHttpUrl("http://[::1]:11434/v1"))

        // LAN/private HTTP hosts and public cleartext HTTP MUST be blocked to prevent key leakage / MITM
        assertFalse(CloudAiClient.isValidHttpUrl("http://192.168.1.42:11434/v1"))
        assertFalse(CloudAiClient.isValidHttpUrl("http://10.0.0.12:8000/v1"))
        assertFalse(CloudAiClient.isValidHttpUrl("http://172.16.4.1:8000/v1"))
        assertFalse(CloudAiClient.isValidHttpUrl("http://ollama.local:11434/v1"))
        assertFalse(CloudAiClient.isValidHttpUrl("http://api.openai.com/v1/chat/completions"))
        assertFalse(CloudAiClient.isValidHttpUrl("http://8.8.8.8/v1"))
        assertFalse(CloudAiClient.isValidHttpUrl("http://172.15.0.1/v1"))
        assertFalse(CloudAiClient.isValidHttpUrl("http://172.32.0.1/v1"))
        assertFalse(CloudAiClient.isValidHttpUrl("javascript:alert(1)"))
    }

    @Test
    fun localModelManagerStrictlyEnforcesHttpsUrls() {
        assertTrue(LocalModelManager.isHttpsUrl("https://huggingface.co/model.gguf"))
        assertFalse(LocalModelManager.isHttpsUrl("http://huggingface.co/model.gguf"))
        assertFalse(LocalModelManager.isHttpsUrl("http://127.0.0.1/model.gguf"))
        assertFalse(LocalModelManager.isHttpsUrl("file:///etc/passwd"))
        assertFalse(LocalModelManager.isHttpsUrl("https://"))
    }

    @Test
    fun tarBz2ExtractorBlocksDecompressionBombsSymlinksAndFileCountFloods() {
        val target = tempFolder.newFolder("extract_out")

        // 1. Decompression byte limit exceeded
        val bombArchive = File(tempFolder.root, "bomb.tar.bz2")
        FileOutputStream(bombArchive).use { fos ->
            BZip2CompressorOutputStream(fos).use { bzo ->
                TarArchiveOutputStream(bzo).use { tos ->
                    val payload = ByteArray(4096) { 0x41 }
                    val entry = TarArchiveEntry("model/weights.bin").apply { size = payload.size.toLong() }
                    tos.putArchiveEntry(entry)
                    tos.write(payload)
                    tos.closeArchiveEntry()
                }
            }
        }
        assertThrows(SecurityException::class.java) {
            TarBz2Extractor.extract(bombArchive, target, maxExtractedBytes = 1024L)
        }

        // 2. Symlink entry blocked
        val symlinkArchive = File(tempFolder.root, "symlink.tar.bz2")
        FileOutputStream(symlinkArchive).use { fos ->
            BZip2CompressorOutputStream(fos).use { bzo ->
                TarArchiveOutputStream(bzo).use { tos ->
                    val link = TarArchiveEntry("model/evil_link", TarArchiveEntry.LF_SYMLINK).apply {
                        linkName = "/etc/passwd"
                    }
                    tos.putArchiveEntry(link)
                    tos.closeArchiveEntry()
                }
            }
        }
        assertThrows(SecurityException::class.java) {
            TarBz2Extractor.extract(symlinkArchive, target)
        }

        // 3. Excessive file count blocked
        val floodArchive = File(tempFolder.root, "flood.tar.bz2")
        FileOutputStream(floodArchive).use { fos ->
            BZip2CompressorOutputStream(fos).use { bzo ->
                TarArchiveOutputStream(bzo).use { tos ->
                    repeat(5) { idx ->
                        val data = "ok".toByteArray()
                        val e = TarArchiveEntry("model/f$idx.txt").apply { size = data.size.toLong() }
                        tos.putArchiveEntry(e)
                        tos.write(data)
                        tos.closeArchiveEntry()
                    }
                }
            }
        }
        assertThrows(SecurityException::class.java) {
            TarBz2Extractor.extract(floodArchive, target, maxExtractedFiles = 3)
        }
    }

    @Test
    fun customDictionaryParserBlocksZipTraversalAndEntryCountFlood() {
        // 1. Traversal entry "../evil.txt" blocked
        val traversalBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry("../evil.txt"))
                zos.write("hello".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()
        assertThrows(SecurityException::class.java) {
            CustomDictionaryParser.parseBytes(traversalBytes)
        }

        // 2. > 256 ZIP entries blocked
        val floodBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                repeat(260) { i ->
                    zos.putNextEntry(ZipEntry("entry_$i.txt"))
                    zos.write("word$i".toByteArray())
                    zos.closeEntry()
                }
            }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            CustomDictionaryParser.parseBytes(floodBytes)
        }
    }

    @Test
    fun permissionBridgeWhitelistsOnlyDeclaredRuntimePermissions() {
        assertTrue(Manifest.permission.RECORD_AUDIO in PermissionBridgeActivity.ALLOWED_PERMISSIONS)
        assertTrue(Manifest.permission.CAMERA in PermissionBridgeActivity.ALLOWED_PERMISSIONS)
        assertFalse(Manifest.permission.READ_MEDIA_IMAGES in PermissionBridgeActivity.ALLOWED_PERMISSIONS)
        assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in PermissionBridgeActivity.ALLOWED_PERMISSIONS)
        assertFalse(Manifest.permission.READ_CONTACTS in PermissionBridgeActivity.ALLOWED_PERMISSIONS)
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in PermissionBridgeActivity.ALLOWED_PERMISSIONS)
    }

    @Test
    fun optimizedKeyboardDistanceAndDamerauLevenshteinAreAccurateAndFast() {
        assertEquals(0.0, SuggestionEngine.weightedKeyboardDistance("hallo", "hallo", "de"), 1e-6)
        // Adjacent keys on German QWERTZ ('d' and 'f') have lower cost than distant keys ('q' and 'm')
        val nearCost = SuggestionEngine.weightedKeyboardDistance("fanke", "danke", "de")
        val farCost = SuggestionEngine.weightedKeyboardDistance("manke", "danke", "de")
        assertTrue("Expected near key cost ($nearCost) < far key cost ($farCost)", nearCost < farCost)
        // Umlaut substitution is cheap (0.25)
        assertEquals(0.25, SuggestionEngine.weightedKeyboardDistance("fuer", "füer", "de"), 1e-6)
        // Transposition in Damerau-Levenshtein
        assertEquals(1, SuggestionEngine.damerauLevenshtein("nciht", "nicht"))
        assertEquals(1, SuggestionEngine.damerauLevenshtein("teh", "the"))

        // Warmup before timing so JIT compilation & Kover instrumentation do not skew CI numbers
        repeat(500) {
            SuggestionEngine.weightedKeyboardDistance("entwiclun", "entwicklung", "de", maxDistance = 2.15)
        }
        val t0 = System.nanoTime()
        repeat(5_000) {
            SuggestionEngine.weightedKeyboardDistance("entwiclun", "entwicklung", "de", maxDistance = 2.15)
        }
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
        assertTrue("Expected 5000 weighted distances < 1000 ms, got $elapsedMs ms", elapsedMs < 1_000.0)
    }

    @Test
    fun optimizedSwipeDecoderFindsExpectedGermanWordFast() {
        val rows = listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")
        val centers = buildList {
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, ch ->
                    add(KeyCenter(ch, x = (c + r * 0.4f) * 100f + 50f, y = r * 120f + 60f))
                }
            }
        }
        val byChar = centers.associateBy { it.char }
        val path = listOf('h', 'a', 'l', 'o').map { ch ->
            val kc = byChar.getValue(ch)
            SwipePoint(kc.x, kc.y)
        }
        val results = SwipeGestureDecoder.decodeSwipe(path, centers, "de-DE")
        assertTrue("Expected 'hallo' in $results", results.any { it.equals("hallo", ignoreCase = true) })
    }
}
