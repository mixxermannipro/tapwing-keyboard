// SPDX-License-Identifier: GPL-3.0-or-later

package com.tapwing.keyboard.ai

import java.io.File
import java.security.MessageDigest

/**
 * Verifies exact byte size and SHA-256 digest of downloaded models and offline dictionary packs
 * before extraction or native library loading.
 */
object DownloadIntegrityVerifier {

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(
        file: File,
        expectedSizeBytes: Long,
        expectedSha256: String,
        precomputedSha256: String? = null
    ): Result<Unit> {
        if (!file.isFile) {
            return Result.failure(IllegalStateException("Downloaded file is missing"))
        }
        val actualSize = file.length()
        if (expectedSizeBytes > 0L && actualSize != expectedSizeBytes) {
            return Result.failure(
                SecurityException("Size mismatch: expected $expectedSizeBytes bytes, got $actualSize")
            )
        }
        val normalizedExpected = expectedSha256.trim().lowercase()
        if (normalizedExpected.length != 64 || normalizedExpected.any { it !in '0'..'9' && it !in 'a'..'f' }) {
            return Result.failure(SecurityException("Missing or invalid expected SHA-256 checksum"))
        }
        val actualHash = (precomputedSha256 ?: sha256Of(file)).trim().lowercase()
        if (!constantTimeEquals(actualHash, normalizedExpected)) {
            return Result.failure(
                SecurityException("SHA-256 checksum mismatch for ${file.name}")
            )
        }
        return Result.success(Unit)
    }

    internal fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }
}
