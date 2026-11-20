package com.tapwing.keyboard.clipboard

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

data class RecentScreenshot(
    val uri: Uri,
    val displayName: String,
    val dateAddedSec: Long,
    val localImagePath: String? = null,
    val ocrText: String? = null
)

/**
 * Monitors recent screenshots (when user grants optional media permission) AND clipboard image URIs,
 * caches local copies for instant thumbnail preview, copies screenshots into system & Tapwing
 * clipboard via FileProvider, and extracts on-device ML Kit OCR text.
 */
object ScreenshotMonitor {

    @Volatile
    private var lastSelfCopiedAtMs: Long = 0L

    @Volatile
    private var lastSelfCopiedUriString: String? = null

    fun requiredMediaPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasMediaPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            requiredMediaPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isSelfCopiedEvent(clipUri: Uri?): Boolean {
        if (clipUri == null) return false
        val elapsed = System.currentTimeMillis() - lastSelfCopiedAtMs
        return elapsed < 2500L && clipUri.toString() == lastSelfCopiedUriString
    }

    fun registerScreenshotObserver(
        context: Context,
        onChanged: () -> Unit
    ): ContentObserver? {
        if (!hasMediaPermission(context)) return null
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onChanged()
            }
        }
        return runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            observer
        }.getOrNull()
    }

    fun unregisterScreenshotObserver(context: Context, observer: ContentObserver?) {
        observer ?: return
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    suspend fun findRecentScreenshot(
        context: Context,
        maxAgeSec: Long = 180L
    ): RecentScreenshot? = withContext(Dispatchers.IO) {
        if (!hasMediaPermission(context)) return@withContext null
        val nowSec = System.currentTimeMillis() / 1000L
        val cutoffSec = nowSec - maxAgeSec

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val args = arrayOf(cutoffSec.toString())
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                sort
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val relCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol).orEmpty()
                    val rel = if (relCol >= 0) cursor.getString(relCol).orEmpty() else ""
                    val isShot = rel.contains("Screenshot", ignoreCase = true) ||
                        name.contains("Screenshot", ignoreCase = true) ||
                        name.contains("Bildschirmfoto", ignoreCase = true)
                    if (!isShot) continue

                    val id = cursor.getLong(idCol)
                    val dateSec = cursor.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    return@withContext RecentScreenshot(
                        uri = uri,
                        displayName = name.ifBlank { "Screenshot" },
                        dateAddedSec = dateSec
                    )
                }
            }
        }
        null
    }

    suspend fun cacheImageLocally(context: Context, sourceUri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "clipboard_images")
            if (!dir.exists()) dir.mkdirs()

            val decoded = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return@withContext null

            val scaled = downscaleIfNeeded(decoded, maxDimension = 2160)
            val outFile = File(dir, "shot_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.PNG, 95, fos)
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            outFile
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
            val clip = ClipData.newUri(context.contentResolver, "Tapwing Screenshot", uri)
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
