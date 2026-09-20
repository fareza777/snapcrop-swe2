package com.sharesafe.app.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * Permanently burns redactions into a copy of the bitmap.
 * Pixelate/blur use downscale-upscale — deterministic, no RenderScript.
 */
object ImageRedactor {

    fun render(
        bitmap: Bitmap,
        regions: List<RedactRegion>,
        defaultStyle: RedactStyle,
        defaultStrength: Float = 1f,
    ): Bitmap {
        val enabled = regions.filter { it.enabled }
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (enabled.isEmpty()) return result
        val canvas = Canvas(result)
        // Blackout last so cosmetic overlaps can never weaken opaque bars.
        enabled.sortedBy { if ((it.styleOverride ?: defaultStyle) == RedactStyle.BLACK) 1 else 0 }
            .forEach { region ->
                val strength = (region.strengthOverride ?: defaultStrength)
                    .coerceIn(MIN_STRENGTH, MAX_STRENGTH)
                when (region.styleOverride ?: defaultStyle) {
                    RedactStyle.BLACK -> opaqueInPlace(result, region.rect)
                    RedactStyle.PIXELATE -> pixelateInPlace(result, canvas, region.rect, strength)
                    RedactStyle.BLUR -> blurInPlace(result, canvas, region.rect, strength)
                }
            }
        return result
    }

    const val MIN_STRENGTH = 0.5f
    const val MAX_STRENGTH = 4f

    private fun opaqueInPlace(bitmap: Bitmap, rect: Rect, color: Int = Color.BLACK) {
        val safe = rect.clippedTo(bitmap) ?: return
        val row = IntArray(safe.width()) { color or (0xFF shl 24) }
        for (y in safe.top until safe.bottom) {
            bitmap.setPixels(row, 0, row.size, safe.left, y, row.size, 1)
        }
    }

    private fun pixelateInPlace(result: Bitmap, canvas: Canvas, rect: Rect, strength: Float) {
        val safe = rect.clippedTo(result) ?: return
        val blockSize = (12 * strength).toInt().coerceIn(4, 96)
        if (safe.width() < 2 || safe.height() < 2) return
        if (safe.width() < blockSize * 2 || safe.height() < blockSize * 2) {
            solidFill(result, canvas, safe)
            return
        }
        scaleRegion(result, canvas, safe, divisor = blockSize, filterUpscale = false)
    }

    private fun blurInPlace(result: Bitmap, canvas: Canvas, rect: Rect, strength: Float) {
        val safe = rect.clippedTo(result) ?: return
        if (safe.width() < 2 || safe.height() < 2) return
        scaleRegion(result, canvas, safe, divisor = (24 * strength).toInt().coerceIn(6, 160),
            filterUpscale = true)
    }

    private fun scaleRegion(
        result: Bitmap,
        canvas: Canvas,
        rect: Rect,
        divisor: Int,
        filterUpscale: Boolean,
    ) {
        var region: Bitmap? = null
        var tiny: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            region = Bitmap.createBitmap(result, rect.left, rect.top, rect.width(), rect.height())
            tiny = Bitmap.createScaledBitmap(
                region,
                (rect.width() / divisor).coerceAtLeast(1),
                (rect.height() / divisor).coerceAtLeast(1),
                true,
            )
            scaled = Bitmap.createScaledBitmap(tiny, rect.width(), rect.height(), filterUpscale)
            canvas.drawBitmap(scaled, rect.left.toFloat(), rect.top.toFloat(), null)
        } finally {
            region?.recycle()
            tiny?.recycle()
            scaled?.recycle()
        }
    }

    private fun Rect.clippedTo(bitmap: Bitmap): Rect? {
        val clipped = Rect(
            left.coerceIn(0, bitmap.width),
            top.coerceIn(0, bitmap.height),
            right.coerceIn(0, bitmap.width),
            bottom.coerceIn(0, bitmap.height),
        )
        return clipped.takeIf { it.width() > 0 && it.height() > 0 }
    }

    /** Average-colour fill so tiny regions are still fully obscured. */
    private fun solidFill(source: Bitmap, canvas: Canvas, rect: Rect) {
        var region: Bitmap? = null
        var one: Bitmap? = null
        try {
            region = Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
            one = Bitmap.createScaledBitmap(region, 1, 1, true)
            val paint = Paint().apply {
                color = one.getPixel(0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawRect(
                rect.left.toFloat(), rect.top.toFloat(),
                rect.right.toFloat(), rect.bottom.toFloat(), paint,
            )
        } finally {
            region?.recycle()
            one?.recycle()
        }
    }
}
