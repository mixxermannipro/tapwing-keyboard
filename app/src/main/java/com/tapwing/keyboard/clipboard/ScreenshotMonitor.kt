// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlin.coroutines.resume

data class RecentScreenshot(
    val uri: Uri,
    val displayName: String,
    val dateAddedSec: Long,
    val localImagePath: String? = null,
    val ocrText: String? = null
)

/**
 * Zero-permission clipboard image and PickVisualMedia OCR helper.
 * Caches copied/picked images locally, provides FileProvider URIs for clipboard paste,
 * and runs on-device ML Kit text recognition without requesting gallery/storage permissions.
 */
object ScreenshotMonitor {

    data class CachedImage(val file: File, val digest: String)

    @Volatile
    private var lastSelfCopiedAtMs: Long = 0L

    @Volatile
    private var lastSelfCopiedUriString: String? = null

    fun isSelfCopiedEvent(clipUri: Uri?): Boolean {
        if (clipUri == null) return false
        val elapsed = System.currentTimeMillis() - lastSelfCopiedAtMs
        return elapsed < 2500L && clipUri.toString() == lastSelfCopiedUriString
    }

    suspend fun cacheImageLocally(context: Context, sourceUri: Uri): CachedImage? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "clipboard_images")
                dir.mkdirs()
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                if (longest <= 0) return@withContext null
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    while (longest / inSampleSize.coerceAtLeast(1) > 4320) {
                        inSampleSize = inSampleSize.coerceAtLeast(1) * 2
                    }
                }
                val decoded = resolver.openInputStream(sourceUri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@withContext null
                val scaled = downscaleIfNeeded(decoded, maxDimension = 2160)
                var outFile: File? = null
                try {
                    val file = File.createTempFile("image_", ".png", dir)
                    outFile = file
                    val digest = MessageDigest.getInstance("SHA-256")
                    DigestOutputStream(file.outputStream().buffered(), digest).use { output ->
                        check(scaled.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                    CachedImage(file, digest.digest().toHexString())
                } catch (failure: Exception) {
                    outFile?.delete()
                    throw failure
                } finally {
                    if (scaled !== decoded) scaled.recycle()
                    decoded.recycle()
                }
            }.getOrNull()
        }

    fun fileProviderUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun copyImageToSystemClipboard(context: Context, localFile: File): Uri? {
        return runCatching {
            val uri = fileProviderUri(context, localFile)
            lastSelfCopiedAtMs = System.currentTimeMillis()
            lastSelfCopiedUriString = uri.toString()
            val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
            val clip = ClipData.newUri(context.contentResolver, "Tapwing Image", uri)
            cm.setPrimaryClip(clip)
            uri
        }.getOrNull()
    }

    suspend fun extractTextFromFile(context: Context, file: File): String {
        if (!file.isFile) return ""
        return extractTextFromImageUri(context, Uri.fromFile(file))
    }

    suspend fun extractTextFromImageUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            suspendCancellableCoroutine<String> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        cont.resume(result.text.trim())
                    }
                    .addOnFailureListener {
                        cont.resume("")
                    }
                    .addOnCompleteListener { recognizer.close() }
            }
        }.getOrDefault("")
    }

    private fun downscaleIfNeeded(bmp: Bitmap, maxDimension: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val longest = maxOf(w, h)
        if (longest <= maxDimension) return bmp
        val ratio = maxDimension.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(
            bmp,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
