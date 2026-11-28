// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Performs one-time migration of sensitive user files from Device-Protected (DE) storage
 * into Credential-Encrypted (CE) storage and securely wipes the legacy DE copy.
 */
internal object StorageMigration {

    /**
     * Copies [deFile] to [ceFile] (if [ceFile] does not already exist) atomically, then overwrites
     * and deletes [deFile] (plus any sibling `.tmp` files) so no sensitive user data remains in DE storage.
     */
    fun migrateDeFileToCe(deFile: File, ceFile: File): Boolean {
        if (!deFile.isFile) return false
        return runCatching {
            val ceParent = ceFile.absoluteFile.parentFile
            if (ceParent != null && !ceParent.exists()) ceParent.mkdirs()

            if (!ceFile.isFile) {
                val tmp = File.createTempFile("migrate-", ".tmp", ceParent)
                try {
                    deFile.inputStream().use { input ->
                        tmp.outputStream().use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                    try {
                        Files.move(
                            tmp.toPath(),
                            ceFile.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(
                            tmp.toPath(),
                            ceFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                } finally {
                    if (tmp.exists()) tmp.delete()
                }
            }
            secureDelete(deFile)
            true
        }.getOrDefault(false)
    }

    /**
     * Overwrites [file] with zeros and deletes it. Also cleans up matching `.tmp` siblings if present.
     */
    fun secureDelete(file: File): Boolean {
        val mainOk = secureWipeAndDelete(file)
        val tmpSibling = file.parentFile?.let { File(it, "${file.name}.tmp") }
        val tmpOk = if (tmpSibling != null && tmpSibling.exists()) {
            secureWipeAndDelete(tmpSibling)
        } else {
            true
        }
        return mainOk && tmpOk
    }

    private fun secureWipeAndDelete(target: File): Boolean {
        if (!target.exists()) return true
        return runCatching {
            if (target.isFile) {
                val len = target.length()
                if (len > 0L) {
                    RandomAccessFile(target, "rw").use { raf ->
                        val zeros = ByteArray(8192)
                        var remaining = len
                        raf.seek(0L)
                        while (remaining > 0L) {
                            val chunk = minOf(remaining, zeros.size.toLong()).toInt()
                            raf.write(zeros, 0, chunk)
                            remaining -= chunk
                        }
                        raf.fd.sync()
                    }
                }
            }
            target.delete()
        }.getOrDefault(false)
    }
}
