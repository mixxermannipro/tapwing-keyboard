package com.tapwing.keyboard.clipboard

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class RecentScreenshot(
    val uri: Uri,
    val displayName: String,
    val dateAddedSec: Long
)

/**
 * Privacy-first on-device OCR helper for user-selected photos and screenshots via the Android
 * System Photo Picker (requires zero broad storage/gallery permissions for Google Play compliance).
 */
object ScreenshotMonitor {

    fun hasMediaPermission(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = false

    suspend fun findRecentScreenshot(@Suppress("UNUSED_PARAMETER") context: Context): RecentScreenshot? =
        null

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
            }
        }.getOrDefault("")
    }
}
