package com.sharesafe.app.core

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object SystemBars {
    fun statusBarHeight(resources: Resources): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    fun navigationBarHeight(resources: Resources): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }
}

/**
 * Strips system bars then detects uniform borders — SnapCrop's base pass
 * (statusbar/nav stripping + corner-colour border detection), minus app profiles.
 */
object AutoCrop {

    private const val COLOR_TOLERANCE = 30
    private const val PADDING = 2

    data class CropResult(val rect: Rect, val method: String)

    fun detect(bitmap: Bitmap, statusBarPx: Int = 0, navBarPx: Int = 0): CropResult {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 100 || h < 100) return CropResult(Rect(0, 0, w, h), "full")

        var contentTop = 0
        var contentBottom = h
        if (statusBarPx in 1 until h / 4) contentTop = statusBarPx
        if (navBarPx in 1 until h / 4) contentBottom = h - navBarPx
        val strippedBars = contentTop > 0 || contentBottom < h

        val borderRect = detectBorders(bitmap, w, contentTop, contentBottom)
        val hasBorders = borderRect.left > 0 || borderRect.top > contentTop ||
            borderRect.right < w || borderRect.bottom < contentBottom

        return when {
            hasBorders -> CropResult(borderRect, if (strippedBars) "border+bars" else "border")
            strippedBars -> CropResult(Rect(0, contentTop, w, contentBottom), "statusbar")
            else -> CropResult(Rect(0, 0, w, h), "full")
        }
    }

    private fun detectBorders(bitmap: Bitmap, w: Int, scanTop: Int, scanBottom: Int): Rect {
        if (scanTop >= scanBottom || w <= 0) return Rect(0, scanTop, w, scanBottom)

        val corners = intArrayOf(
            bitmap.getPixel(0, scanTop.coerceIn(0, bitmap.height - 1)),
            bitmap.getPixel(w - 1, scanTop.coerceIn(0, bitmap.height - 1)),
            bitmap.getPixel(0, (scanBottom - 1).coerceIn(0, bitmap.height - 1)),
            bitmap.getPixel(w - 1, (scanBottom - 1).coerceIn(0, bitmap.height - 1)),
        )
        val borderColor = corners.groupBy { it }.maxByOrNull { it.value.size }?.key ?: corners[0]

        val top = findTopEdge(bitmap, w, scanTop, scanBottom, borderColor)
        val bottom = findBottomEdge(bitmap, w, scanTop, scanBottom, borderColor)
        val left = findLeftEdge(bitmap, w, top, bottom, borderColor)
        val right = findRightEdge(bitmap, w, top, bottom, borderColor)

        val cropLeft = max(0, left - PADDING)
        val cropTop = max(scanTop, top - PADDING)
        val cropRight = min(w, right + PADDING)
        val cropBottom = min(scanBottom, bottom + PADDING)

        val minW = (w * 0.1f).toInt()
        val minH = ((scanBottom - scanTop) * 0.1f).toInt()
        return if (cropRight - cropLeft < minW || cropBottom - cropTop < minH) {
            Rect(0, scanTop, w, scanBottom)
        } else {
            Rect(cropLeft, cropTop, cropRight, cropBottom)
        }
    }

    private fun findTopEdge(bitmap: Bitmap, w: Int, scanTop: Int, scanBottom: Int, borderColor: Int): Int {
        val sampleX = IntArray(5) { (w * (it + 1) / 6f).toInt().coerceIn(0, w - 1) }
        val limit = scanTop + ((scanBottom - scanTop) * 0.45f).toInt()
        for (y in scanTop until limit) {
            val refColor = bitmap.getPixel(sampleX[0], y)
            var nonUniform = 0
            for (sx in sampleX) {
                if (!colorsMatch(bitmap.getPixel(sx, y), refColor)) nonUniform++
            }
            val isUniform = nonUniform < 2 && isRowUniform(bitmap, y, w)
            val matchesBorder = borderColor == 0 || colorsMatch(refColor, borderColor)
            if (!isUniform || !matchesBorder) return max(scanTop, y)
        }
        return scanTop
    }

    private fun findBottomEdge(bitmap: Bitmap, w: Int, scanTop: Int, scanBottom: Int, borderColor: Int): Int {
        val sampleX = IntArray(5) { (w * (it + 1) / 6f).toInt().coerceIn(0, w - 1) }
        val limit = scanTop + ((scanBottom - scanTop) * 0.55f).toInt()
        for (y in scanBottom - 1 downTo limit) {
            val refColor = bitmap.getPixel(sampleX[0], y)
            var nonUniform = 0
            for (sx in sampleX) {
                if (!colorsMatch(bitmap.getPixel(sx, y), refColor)) nonUniform++
            }
            val isUniform = nonUniform < 2 && isRowUniform(bitmap, y, w)
            val matchesBorder = borderColor == 0 || colorsMatch(refColor, borderColor)
            if (!isUniform || !matchesBorder) return min(scanBottom, y + 1)
        }
        return scanBottom
    }

    private fun findLeftEdge(bitmap: Bitmap, w: Int, top: Int, bottom: Int, borderColor: Int): Int {
        val sampleY = IntArray(5) {
            (top + (bottom - top) * (it + 1) / 6f).toInt().coerceIn(top, max(top, bottom - 1))
        }
        for (x in 0 until (w * 0.45f).toInt()) {
            var nonUniform = 0
            val refColor = bitmap.getPixel(x, sampleY[0])
            for (sy in sampleY) {
                if (!colorsMatch(bitmap.getPixel(x, sy), refColor)) nonUniform++
            }
            val isUniform = nonUniform < 2 && isColumnUniform(bitmap, x, top, bottom)
            val matchesBorder = borderColor == 0 || colorsMatch(refColor, borderColor)
            if (!isUniform || !matchesBorder) return max(0, x)
        }
        return 0
    }

    private fun findRightEdge(bitmap: Bitmap, w: Int, top: Int, bottom: Int, borderColor: Int): Int {
        val sampleY = IntArray(5) {
            (top + (bottom - top) * (it + 1) / 6f).toInt().coerceIn(top, max(top, bottom - 1))
        }
        for (x in w - 1 downTo (w * 0.55f).toInt()) {
            var nonUniform = 0
            val refColor = bitmap.getPixel(x, sampleY[0])
            for (sy in sampleY) {
                if (!colorsMatch(bitmap.getPixel(x, sy), refColor)) nonUniform++
            }
            val isUniform = nonUniform < 2 && isColumnUniform(bitmap, x, top, bottom)
            val matchesBorder = borderColor == 0 || colorsMatch(refColor, borderColor)
            if (!isUniform || !matchesBorder) return min(w, x + 1)
        }
        return w
    }

    private fun isRowUniform(bitmap: Bitmap, y: Int, w: Int): Boolean {
        val step = max(1, w / 20)
        val refColor = bitmap.getPixel(0, y)
        var matches = 0
        var total = 0
        var x = 0
        while (x < w) {
            total++
            if (colorsMatch(bitmap.getPixel(x, y), refColor)) matches++
            x += step
        }
        return matches.toFloat() / total > 0.85f
    }

    private fun isColumnUniform(bitmap: Bitmap, x: Int, top: Int, bottom: Int): Boolean {
        val h = bottom - top
        if (h <= 0) return true
        val step = max(1, h / 20)
        val refColor = bitmap.getPixel(x, top)
        var matches = 0
        var total = 0
        var y = top
        while (y < bottom) {
            total++
            if (colorsMatch(bitmap.getPixel(x, y), refColor)) matches++
            y += step
        }
        return matches.toFloat() / total > 0.85f
    }

    private fun colorsMatch(c1: Int, c2: Int): Boolean {
        val r1 = (c1 shr 16) and 0xFF; val g1 = (c1 shr 8) and 0xFF; val b1 = c1 and 0xFF
        val r2 = (c2 shr 16) and 0xFF; val g2 = (c2 shr 8) and 0xFF; val b2 = c2 and 0xFF
        return abs(r1 - r2) <= COLOR_TOLERANCE &&
            abs(g1 - g2) <= COLOR_TOLERANCE &&
            abs(b1 - b2) <= COLOR_TOLERANCE
    }
}
