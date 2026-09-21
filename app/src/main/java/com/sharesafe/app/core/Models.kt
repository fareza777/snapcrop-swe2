package com.sharesafe.app.core

import android.graphics.Rect
import java.util.UUID

enum class RedactStyle(val label: String) {
    BLUR("Blur"),
    PIXELATE("Pixelate"),
    BLACK("Blackout"),
    /** Cover text with a plausible-looking masked substitute — doesn't read as redacted. */
    CLOAK("Cloak"),
    /** Cover with an emoji — natural for faces. */
    EMOJI("Emoji"),
}

enum class RegionKind(val label: String, val badge: String) {
    EMAIL("Email", "EM"),
    PHONE("Phone", "PH"),
    CARD("Payment card", "CC"),
    NUMBER("Sensitive number", "#"),
    SECRET("Secret / token", "SK"),
    NETWORK("IP / network", "IP"),
    ADDRESS("Address", "AD"),
    DATETIME("Date / time", "DT"),
    TRACKING("Tracking no.", "TR"),
    CUSTOM("Custom", "CU"),
    CODE("QR / barcode", "QR"),
    FACE("Face", "FC"),
    MANUAL("Manual", "MN"),
}

/** One redaction region in full-image pixel coordinates. */
data class RedactRegion(
    val id: String,
    val rect: Rect,
    val kind: RegionKind,
    val enabled: Boolean = true,
    /** null = follow the session default style. */
    val styleOverride: RedactStyle? = null,
    /** null = follow the session default strength (1.0). */
    val strengthOverride: Float? = null,
    val detail: String = "",
    /** 0..1 detector confidence; below CONFIDENT_MIN renders as a "maybe" dashed region. */
    val confidence: Float = 1f,
    /** The exact OCR text that produced this region — powers cloak + apply-to-similar. */
    val sourceText: String = "",
    /** Emoji used when style == EMOJI; empty = default. */
    val emoji: String = "",
    /** Freehand marker stroke — x,y pairs in image coords. Non-null => drawn as a thick stroke. */
    val strokePoints: FloatArray? = null,
) {
    val effectiveStyleKey get() = id
    val isLowConfidence get() = confidence < CONFIDENT_MIN

    companion object {
        const val CONFIDENT_MIN = 0.7f

        fun new(
            rect: Rect,
            kind: RegionKind,
            style: RedactStyle? = null,
            strength: Float? = null,
            detail: String = "",
            confidence: Float = 1f,
            sourceText: String = "",
        ) =
            RedactRegion(
                id = UUID.randomUUID().toString().take(12),
                rect = Rect(rect),
                kind = kind,
                styleOverride = style,
                strengthOverride = strength,
                detail = detail,
                confidence = confidence,
                sourceText = sourceText,
            )
    }
}

/** User-authored detection rule — wildcard (#,*,?) or raw regex — shown as CUSTOM regions. */
data class CustomRule(val label: String, val pattern: String) {
    fun toRegex(): Regex? {
        val p = pattern.trim()
        if (p.isEmpty()) return null
        val looksLikeRegex = p.any { it in "\\()[]{}+^$|" }
        val body = if (looksLikeRegex) p else buildString {
            p.forEach { ch ->
                when (ch) {
                    '#' -> append("\\d")
                    '*' -> append(".*")
                    '?' -> append("[A-Za-z0-9]")
                    else -> append(Regex.escape(ch.toString()))
                }
            }
        }
        return runCatching {
            Regex(body, setOf(RegexOption.IGNORE_CASE))
        }.getOrNull()
    }
}

fun Rect.copyRect() = Rect(this)

fun Rect.padded(fx: Float, fy: Float, maxW: Int, maxH: Int): Rect {
    val padX = (width() * fx).toInt().coerceAtLeast(6)
    val padY = (height() * fy).toInt().coerceAtLeast(5)
    return Rect(
        (left - padX).coerceIn(0, maxW),
        (top - padY).coerceIn(0, maxH),
        (right + padX).coerceIn(0, maxW),
        (bottom + padY).coerceIn(0, maxH),
    )
}

fun Rect.normalized(): Rect =
    Rect(minOf(left, right), minOf(top, bottom), maxOf(left, right), maxOf(top, bottom))

fun Rect.clampTo(width: Int, height: Int, minSize: Int = 8): Rect? {
    val n = normalized()
    val l = n.left.coerceIn(0, width - minSize)
    val t = n.top.coerceIn(0, height - minSize)
    val r = n.right.coerceIn(l + minSize, width)
    val b = n.bottom.coerceIn(t + minSize, height)
    return if (r - l >= minSize && b - t >= minSize) Rect(l, t, r, b) else null
}
