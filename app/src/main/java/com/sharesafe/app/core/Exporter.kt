package com.sharesafe.app.core

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    PNG("PNG", "png", "image/png"),
    JPEG("JPEG", "jpg", "image/jpeg"),
    WEBP("WebP", "webp", "image/webp"),
    ;

    fun compressFormat(): Bitmap.CompressFormat = when (this) {
        PNG -> Bitmap.CompressFormat.PNG
        JPEG -> Bitmap.CompressFormat.JPEG
        WEBP -> if (Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }
    }
}

object Exporter {

    /** JPEG/WebP can't hold alpha — flatten onto white so transparent corners stay clean. */
    private fun flattenIfNeeded(src: Bitmap, format: ExportFormat): Bitmap {
        if (format == ExportFormat.PNG || !src.hasAlpha()) return src
        val flat = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, 0f, 0f, null)
        }
        return flat
    }

    /** Saves into Pictures/ShareSafe via MediaStore — no permission needed on API 29+. */
    suspend fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        format: ExportFormat = ExportFormat.PNG,
    ): Uri? = withContext(Dispatchers.IO) {
        val name = "ShareSafe-${timestamp()}.${format.ext}"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ShareSafe")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver: ContentResolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null
        try {
            val out = flattenIfNeeded(bitmap, format)
            resolver.openOutputStream(uri)?.use { stream ->
                if (!out.compress(format.compressFormat(), 95, stream)) {
                    resolver.delete(uri, null, null)
                    return@withContext null
                }
            } ?: run {
                resolver.delete(uri, null, null)
                return@withContext null
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null, null,
            )
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /** Writes to cache and returns a grantable content Uri for ACTION_SEND. */
    suspend fun shareUri(
        context: Context,
        bitmap: Bitmap,
        format: ExportFormat = ExportFormat.PNG,
    ): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "ShareSafe-${timestamp()}.${format.ext}")
        val out = flattenIfNeeded(bitmap, format)
        file.outputStream().use { out.compress(format.compressFormat(), 95, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareIntent(uri: Uri, mime: String = "image/png"): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
