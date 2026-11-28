// SPDX-License-Identifier: GPL-3.0-or-later

package com.tapwing.keyboard

import com.tapwing.keyboard.ai.DownloadIntegrityVerifier
import com.tapwing.keyboard.ai.LocalModelCatalog
import com.tapwing.keyboard.dictionary.OfflineDictionaryPackManager
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelIntegrityAndArchiveSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun verifyAcceptsExactSizeAndSha256AndRejectsTampering() {
        val sample = File(tempFolder.root, "model.bin").apply {
            writeBytes("tapwing-verified-payload".toByteArray())
        }
        val expectedHash = DownloadIntegrityVerifier.sha256Of(sample)
        val expectedSize = sample.length()

        assertTrue(DownloadIntegrityVerifier.verify(sample, expectedSize, expectedHash).isSuccess)
        assertTrue(DownloadIntegrityVerifier.verify(sample, expectedSize + 1, expectedHash).isFailure)
        val corruptedHash = (if (expectedHash.first() == '0') '1' else '0') + expectedHash.drop(1)
        assertTrue(DownloadIntegrityVerifier.verify(sample, expectedSize, corruptedHash).isFailure)
        assertTrue(DownloadIntegrityVerifier.verify(sample, expectedSize, "invalid-sha").isFailure)
    }

    @Test
    fun everyCatalogModelHasPinnedHttpsUrlExactSizeSha256AndLicense() {
        val hexRegex = Regex("^[0-9a-f]{64}$")
        assertTrue("Expected non-empty model catalog", LocalModelCatalog.allModels.isNotEmpty())
        LocalModelCatalog.allModels.forEach { spec ->
            assertTrue("${spec.id} must use HTTPS", spec.downloadUrl.startsWith("https://"))
            assertTrue("${spec.id} must have exactSizeBytes > 0", spec.exactSizeBytes > 0L)
            assertTrue("${spec.id} must have 64-hex sha256", hexRegex.matches(spec.sha256))
            assertTrue("${spec.id} must declare licenseName", spec.licenseName.isNotBlank())
            assertTrue("${spec.id} must declare licenseUrl", spec.licenseUrl.startsWith("https://"))
            assertTrue("${spec.id} must declare attributionNotice", spec.attributionNotice.isNotBlank())
        }
    }

    @Test
    fun everyDictionaryPackHasCommitPinnedUrlExactSizeSha256AndLicense() {
        val hexRegex = Regex("^[0-9a-f]{64}$")
        assertTrue("Expected non-empty pack catalog", OfflineDictionaryPackManager.availablePacks.isNotEmpty())
        OfflineDictionaryPackManager.availablePacks.forEach { pack ->
            assertTrue("${pack.langCode} must use HTTPS", pack.downloadUrl.startsWith("https://"))
            assertFalse("${pack.langCode} must not use mutable refs/heads URL", "/refs/heads/" in pack.downloadUrl)
            assertTrue("${pack.langCode} must have exactSizeBytes > 0", pack.exactSizeBytes > 0L)
            assertTrue("${pack.langCode} must have 64-hex sha256", hexRegex.matches(pack.sha256))
            assertTrue("${pack.langCode} must declare licenseName", pack.licenseName.isNotBlank())
        }
    }

    private fun assertFalse(message: String, condition: Boolean) {
        org.junit.Assert.assertFalse(message, condition)
    }
}
