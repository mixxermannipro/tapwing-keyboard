package com.tapwing.keyboard.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Crash-safe atomic file writer: writes to a sibling temp file first, then atomically moves it
 * into place (with safe fallback when the underlying filesystem does not support ATOMIC_MOVE).
 */
internal object AtomicFileWriter {
    fun writeTextAtomically(target: File, content: String, tempPrefix: String = "tapwing-"): Boolean {
        return runCatching {
            val parent = target.absoluteFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            val tmp = File.createTempFile(tempPrefix, ".tmp", parent)
            try {
                tmp.writeText(content, Charsets.UTF_8)
                try {
                    Files.move(
                        tmp.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        tmp.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }.isSuccess
    }
}
