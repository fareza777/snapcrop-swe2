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
        // Blackout/cloak/marker last so cosmetic overlaps can never weaken them.
        enabled.sortedBy {
            val s = it.styleOverride ?: defaultStyle
            if (it.strokePoints != null || s == RedactStyle.BLACK ||
                s == RedactStyle.CLOAK || s == RedactStyle.EMOJI
            ) 1 else 0
        }.forEach { region ->
                val strength = (region.strengthOverride ?: defaultStrength)
                    .coerceIn(MIN_STRENGTH, MAX_STRENGTH)
                if (region.strokePoints != null) {
                    strokeInPlace(result, canvas, region.strokePoints, strength)
                } else when (region.styleOverride ?: defaultStyle) {
                    RedactStyle.BLACK -> opaqueInPlace(result, region.rect)
                    RedactStyle.PIXELATE -> pixelateInPlace(result, canvas, region.rect, strength)
                    RedactStyle.BLUR -> blurInPlace(result, canvas, region.rect, strength)
                    RedactStyle.CLOAK -> cloakInPlace(result, canvas, region)
                    RedactStyle.EMOJI -> emojiInPlace(result, canvas, region)
                }
            }
        return result
    }

    const val MIN_STRENGTH = 0.5f
    const val MAX_STRENGTH = 4f

    fun pixelateBlockSize(strength: Float): Int =
        (12 * strength.coerceIn(MIN_STRENGTH, MAX_STRENGTH)).toInt().coerceIn(4, 96)

    fun blurDivisor(strength: Float): Int =
        (24 * strength.coerceIn(MIN_STRENGTH, MAX_STRENGTH)).toInt().coerceIn(6, 160)

    private fun opaqueInPlace(bitmap: Bitmap, rect: Rect, color: Int = Color.BLACK) {
        val safe = rect.clippedTo(bitmap) ?: return
        val row = IntArray(safe.width()) { color or (0xFF shl 24) }
        for (y in safe.top until safe.bottom) {
            bitmap.setPixels(row, 0, row.size, safe.left, y, row.size, 1)
        }
    }

    private fun pixelateInPlace(result: Bitmap, canvas: Canvas, rect: Rect, strength: Float) {
        val safe = rect.clippedTo(result) ?: return
        val blockSize = pixelateBlockSize(strength)
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
        scaleRegion(result, canvas, safe, divisor = blurDivisor(strength),
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

    /** Freehand marker — a thick opaque polyline following the user's stroke. */
    private fun strokeInPlace(result: Bitmap, canvas: Canvas, points: FloatArray, strength: Float) {
        if (points.size < 4) {
            // Degenerate tap — just cover the bounding area.
            return
        }
        val width = (26f * strength.coerceIn(MIN_STRENGTH, MAX_STRENGTH))
            .coerceIn(10f, result.width * 0.08f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = android.graphics.Path()
        path.moveTo(points[0], points[1])
        var i = 2
        while (i + 1 < points.size) {
            path.lineTo(points[i], points[i + 1])
            i += 2
        }
        canvas.drawPath(path, paint)
    }

    /**
     * Cloak — wipe the region to its sampled surrounding colour, then draw a
     * masked substitute ("j•••@c•••.com") in place. Reads as real content.
     */
    private fun cloakInPlace(result: Bitmap, canvas: Canvas, region: RedactRegion) {
        val safe = region.rect.clippedTo(result) ?: return
        solidFill(result, canvas, safe)
        val replacement = Cloaker.substitute(
            region.kind,
            region.sourceText.ifEmpty { region.detail },
        )
        if (replacement.isBlank()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 60, 66, 74)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = false
        }
        // Fit text to the rect: start at 80% of height, shrink if it overflows.
        var size = safe.height() * 0.72f
        paint.textSize = size
        val w = paint.measureText(replacement)
        if (w > safe.width() * 0.94f && w > 0) {
            size *= (safe.width() * 0.94f) / w
            paint.textSize = size
        }
        val baseline = safe.exactCenterY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(replacement, safe.exactCenterX(), baseline, paint)
    }

    /** Emoji cover — centred, scaled to the region. */
    private fun emojiInPlace(result: Bitmap, canvas: Canvas, region: RedactRegion) {
        val safe = region.rect.clippedTo(result) ?: return
        solidFill(result, canvas, safe)
        val emoji = region.emoji.ifEmpty { DEFAULT_EMOJI }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = minOf(safe.width(), safe.height()) * 0.82f
        }
        val baseline = safe.exactCenterY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(emoji, safe.exactCenterX(), baseline, paint)
    }

    const val DEFAULT_EMOJI = "\uD83D\uDE0A" // 😊

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
