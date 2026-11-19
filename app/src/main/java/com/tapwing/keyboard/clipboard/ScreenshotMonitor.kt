package com.tapwing.keyboard.clipboard

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
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
 * Checks MediaStore for a screenshot captured within the last 120 seconds (if media permission
 * is granted) and extracts text on-device using ML Kit when the user taps the suggestion chip.
 */
object ScreenshotMonitor {

    fun hasMediaPermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun findRecentScreenshot(context: Context): RecentScreenshot? = withContext(Dispatchers.IO) {
        if (!hasMediaPermission(context)) return@withContext null
        val cutoffSec = (System.currentTimeMillis() / 1000L) - 120L
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(cutoffSec.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val pathCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol).orEmpty()
                    val relPath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                    val isScreenshot = name.contains("screenshot", ignoreCase = true) ||
                        relPath.contains("screenshot", ignoreCase = true)
                    if (isScreenshot) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        return@withContext RecentScreenshot(
                            uri = uri,
                            displayName = name,
                            dateAddedSec = cursor.getLong(dateCol)
                        )
                    }
                }
            }
            null
        }.getOrNull()
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
            }
        }.getOrDefault("")
    }
}
