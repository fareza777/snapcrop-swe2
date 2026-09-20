package com.sharesafe.app.core

import android.graphics.Rect
import java.util.UUID

enum class RedactStyle(val label: String) {
    BLUR("Blur"),
    PIXELATE("Pixelate"),
    BLACK("Blackout"),
}

enum class RegionKind(val label: String, val badge: String) {
    EMAIL("Email", "EM"),
    PHONE("Phone", "PH"),
    CARD("Payment card", "CC"),
    NUMBER("Sensitive number", "#"),
    SECRET("Secret / token", "SK"),
    NETWORK("IP / network", "IP"),
    ADDRESS("Address", "AD"),
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
    val detail: String = "",
) {
    val effectiveStyleKey get() = id

    companion object {
        fun new(rect: Rect, kind: RegionKind, style: RedactStyle? = null, detail: String = "") =
            RedactRegion(
                id = UUID.randomUUID().toString().take(12),
                rect = Rect(rect),
                kind = kind,
                styleOverride = style,
                detail = detail,
            )
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
