package com.sharesafe.app.core

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageLoader {

    private const val MAX_DIMENSION = 3000
    /** Detectors get a less-downsampled copy so small text survives ML Kit's thresholds. */
    private const val DETECT_MAX_DIMENSION = 4200

    /** Decodes a content Uri to a correctly-rotated, size-capped Bitmap. */
    suspend fun load(resolver: ContentResolver, uri: Uri): Bitmap =
        load(resolver, uri, MAX_DIMENSION)

    /** Higher-resolution copy used only during detection; caller recycles it. */
    suspend fun loadForDetection(resolver: ContentResolver, uri: Uri): Bitmap =
        load(resolver, uri, DETECT_MAX_DIMENSION)

    private suspend fun load(
        resolver: ContentResolver,
        uri: Uri,
        maxDimension: Int,
    ): Bitmap = withContext(Dispatchers.IO) {
        val bounds = resolver.openInputStream(uri)?.use { input ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, opts)
            opts
        } ?: error("Cannot open image")

        val rawW = bounds.outWidth
        val rawH = bounds.outHeight
        require(rawW > 0 && rawH > 0) { "Not a decodable image" }

        var sample = 1
        while (rawW / (sample * 2) >= maxDimension || rawH / (sample * 2) >= maxDimension) {
            sample *= 2
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
                if (sample > 1) decoder.setTargetSampleSize(sample)
            }
            decoded
        } else {
            val bitmap = resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inMutable = true
                })
            } ?: error("Cannot decode image")
            applyExifRotation(resolver, uri, bitmap)
        }
    }

    private fun applyExifRotation(
        resolver: ContentResolver,
        uri: Uri,
        bitmap: Bitmap,
    ): Bitmap {
        val rotation = resolver.openInputStream(uri)?.use { input ->
            when (
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation == 0f) return bitmap
        val m = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
