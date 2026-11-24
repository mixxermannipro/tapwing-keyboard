package com.tapwing.keyboard.ai

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Extracts `.tar.bz2` model archives safely with Zip-Slip path validation,
 * single-root folder flattening, and unnecessary sample/doc filtering.
 */
object TarBz2Extractor {

    data class ExtractResult(
        val extractedFiles: Int,
        val extractedBytes: Long
    )

    fun extract(
        archiveFile: File,
        targetDir: File,
        skipDirs: Set<String> = setOf("test_wavs"),
        skipFiles: Set<String> = setOf("readme.md", ".gitattributes", ".ds_store"),
        onProgress: ((filesExtracted: Int, bytesExtracted: Long) -> Unit)? = null
    ): ExtractResult {
        if (!targetDir.exists()) targetDir.mkdirs()
        val canonicalRoot = targetDir.canonicalPath
        val prefixWithSep = if (canonicalRoot.endsWith(File.separator)) canonicalRoot else canonicalRoot + File.separator

        var filesCount = 0
        var totalBytes = 0L
        var singleTopDir: String? = null
        var topDirDecided = false
        val buffer = ByteArray(32 * 1024)

        BufferedInputStream(archiveFile.inputStream(), 64 * 1024).use { fileIn ->
            BZip2CompressorInputStream(fileIn).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    while (true) {
                        val entry: TarArchiveEntry = tarIn.nextEntry ?: break
                        val cleanSegments = sanitizeEntryPath(entry.name) ?: continue
                        if (cleanSegments.isEmpty()) continue

                        if (!topDirDecided) {
                            topDirDecided = true
                            if (cleanSegments.size >= 2 || entry.isDirectory) {
                                singleTopDir = cleanSegments.first()
                            }
                        } else if (singleTopDir != null && cleanSegments.first() != singleTopDir) {
                            // Multiple distinct top-level roots: do not strip further entries.
                            singleTopDir = null
                        }

                        val relativeSegments = if (singleTopDir != null && cleanSegments.first() == singleTopDir) {
                            cleanSegments.drop(1)
                        } else {
                            cleanSegments
                        }
                        if (relativeSegments.isEmpty()) continue

                        val fileNameLower = relativeSegments.last().lowercase()
                        if (relativeSegments.dropLast(1).any { it.lowercase() in skipDirs } ||
                            fileNameLower in skipFiles ||
                            fileNameLower.startsWith("._")
                        ) {
                            continue
                        }

                        if (entry.isDirectory) {
                            val dir = resolveSafely(targetDir, prefixWithSep, relativeSegments)
                            if (!dir.exists()) dir.mkdirs()
                            continue
                        }

                        if (!entry.isFile) continue

                        val outFile = resolveSafely(targetDir, prefixWithSep, relativeSegments)
                        outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

                        FileOutputStream(outFile).use { out ->
                            while (true) {
                                val read = tarIn.read(buffer)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                                totalBytes += read
                            }
                            out.flush()
                        }
                        filesCount++
                        onProgress?.invoke(filesCount, totalBytes)
                    }
                }
            }
        }
        return ExtractResult(filesCount, totalBytes)
    }

    internal fun sanitizeEntryPath(rawName: String): List<String>? {
        if (rawName.contains('\\') || rawName.contains('\u0000')) return null
        var trimmed = rawName.trim()
        while (trimmed.startsWith("./")) trimmed = trimmed.removePrefix("./")
        trimmed = trimmed.trimStart('/').trimEnd('/')
        if (trimmed.isEmpty()) return emptyList()
        val segments = trimmed.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) {
            throw SecurityException("Blocked unsafe archive entry path: $rawName")
        }
        return segments
    }

    private fun resolveSafely(root: File, canonicalPrefix: String, segments: List<String>): File {
        var current = root
        for (seg in segments) {
            current = File(current, seg)
        }
        val canonicalDest = current.canonicalPath
        if (!canonicalDest.startsWith(canonicalPrefix)) {
            throw SecurityException("Blocked archive path outside target directory: $canonicalDest")
        }
        return current
    }
}
