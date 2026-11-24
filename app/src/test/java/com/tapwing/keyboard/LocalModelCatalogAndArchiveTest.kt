package com.tapwing.keyboard

import com.tapwing.keyboard.ai.LocalModelCatalog
import com.tapwing.keyboard.ai.LocalModelKind
import com.tapwing.keyboard.ai.TarBz2Extractor
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class LocalModelCatalogAndArchiveTest {

    @Test
    fun catalogEntriesHaveUniqueIdsValidUrlsAndSherpaRequiredFiles() {
        val ids = LocalModelCatalog.allModels.map { it.id }
        assertEquals(ids.size, ids.toSet().size)

        for (spec in LocalModelCatalog.allModels) {
            assertTrue("${spec.id} downloadUrl must be https", spec.downloadUrl.startsWith("https://"))
            assertTrue("${spec.id} repoUrl must be https", spec.repoUrl.startsWith("https://"))
            assertTrue("${spec.id} summaryDe non-blank", spec.summaryDe.isNotBlank())
            assertTrue("${spec.id} summaryEn non-blank", spec.summaryEn.isNotBlank())
            assertTrue("${spec.id} approxSizeMb > 0", spec.approxSizeMb > 0)
            if (spec.kind.isSherpa) {
                assertTrue("${spec.id} must declare .tar.bz2 fileName", spec.fileName.endsWith(".tar.bz2"))
                assertTrue("${spec.id} must declare requiredFiles", spec.requiredFiles.isNotEmpty())
                assertTrue("${spec.id} must include tokens.txt", "tokens.txt" in spec.requiredFiles)
            }
        }

        val kroko = LocalModelCatalog.findById("sherpa-streaming-zipformer-de")!!
        assertEquals(LocalModelKind.SHERPA_STREAMING, kroko.kind)
        assertTrue(kroko.supportsStreaming)
        assertTrue(kroko.supportsLocale("de-AT"))
        assertFalse(kroko.supportsLocale("en-US"))

        val parakeet = LocalModelCatalog.findById("sherpa-parakeet-ultra-0.6b-int8")!!
        assertTrue(parakeet.supportsLocale("pl-PL"))
        assertTrue(parakeet.supportsLocale("de-DE"))
        assertFalse(parakeet.supportsLocale("ja-JP"))

        val parakeetV3 = LocalModelCatalog.findById("sherpa-parakeet-v3-0.6b-int8")!!
        assertTrue(parakeetV3.displayName.contains("NVIDIA"))
        assertTrue(parakeetV3.supportsLocale("de-DE"))
        assertTrue(parakeetV3.supportsLocale("en-US"))
        assertTrue(parakeetV3.supportsLocale("pl-PL"))
        assertFalse(parakeetV3.supportsLocale("ja-JP"))

        val parakeetV2 = LocalModelCatalog.findById("sherpa-parakeet-v2-en-int8")!!
        assertTrue(parakeetV2.displayName.contains("NVIDIA"))
        assertTrue(parakeetV2.supportsLocale("en-US"))
        assertTrue(parakeetV2.supportsLocale("en-GB"))
        assertFalse(parakeetV2.supportsLocale("de-DE"))
    }

    @Test
    fun tarBz2ExtractorFlattensSingleTopFolderAndSkipsTestWavsAndDocs() {
        val dir = Files.createTempDirectory("tar-bz2-test").toFile()
        try {
            val archive = File(dir, "model.tar.bz2")
            writeSyntheticTarBz2(
                archive,
                mapOf(
                    "sherpa-model-v1/encoder.int8.onnx" to "enc".toByteArray(),
                    "sherpa-model-v1/decoder.int8.onnx" to "dec".toByteArray(),
                    "sherpa-model-v1/joiner.int8.onnx" to "joi".toByteArray(),
                    "sherpa-model-v1/tokens.txt" to "a 1\nb 2\n".toByteArray(),
                    "sherpa-model-v1/README.md" to "# ignored".toByteArray(),
                    "sherpa-model-v1/._AppleDouble" to byteArrayOf(0, 1),
                    "sherpa-model-v1/test_wavs/sample.wav" to byteArrayOf(1, 2, 3, 4)
                )
            )

            val outDir = File(dir, "extracted")
            val result = TarBz2Extractor.extract(archive, outDir)

            assertEquals(4, result.extractedFiles)
            assertTrue(File(outDir, "encoder.int8.onnx").isFile)
            assertTrue(File(outDir, "decoder.int8.onnx").isFile)
            assertTrue(File(outDir, "joiner.int8.onnx").isFile)
            assertTrue(File(outDir, "tokens.txt").isFile)
            assertFalse(File(outDir, "README.md").exists())
            assertFalse(File(outDir, "._AppleDouble").exists())
            assertFalse(File(outDir, "test_wavs").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun tarBz2ExtractorBlocksPathTraversalZipSlipAttempts() {
        val dir = Files.createTempDirectory("tar-bz2-slip-test").toFile()
        try {
            val archive = File(dir, "evil.tar.bz2")
            writeSyntheticTarBz2(
                archive,
                mapOf("foo/../../escape.txt" to "pwn".toByteArray())
            )
            var thrown = false
            try {
                TarBz2Extractor.extract(archive, File(dir, "target"))
            } catch (_: SecurityException) {
                thrown = true
            }
            assertTrue("Expected SecurityException on path traversal", thrown)
            assertFalse(File(dir, "escape.txt").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun writeSyntheticTarBz2(target: File, entries: Map<String, ByteArray>) {
        val rawOut = ByteArrayOutputStream()
        BZip2CompressorOutputStream(rawOut).use { bz ->
            TarArchiveOutputStream(bz).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                for ((name, data) in entries) {
                    val entry = TarArchiveEntry(name)
                    entry.size = data.size.toLong()
                    tar.putArchiveEntry(entry)
                    tar.write(data)
                    tar.closeArchiveEntry()
                }
            }
        }
        target.writeBytes(rawOut.toByteArray())
    }
}
