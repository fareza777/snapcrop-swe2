package com.sharesafe.app.core

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

enum class BackgroundKind(val label: String) {
    NONE("Original"),
    MIDNIGHT("Midnight"),
    CLOUD("Cloud"),
    SUNSET("Sunset"),
    OCEAN("Ocean"),
    GRAPE("Grape"),
    MINT("Mint"),
    CARBON("Carbon"),
}

data class BeautifyOptions(
    /** Padding in output px around the image. 0 = disabled. */
    val paddingPx: Int = 0,
    /** Corner radius in output px applied to the image. */
    val cornerRadiusPx: Float = 0f,
    val background: BackgroundKind = BackgroundKind.NONE,
    val shadow: Boolean = true,
)

/** Composes the final share image: background + padding + rounded corners. */
object Beautifier {

    fun render(src: Bitmap, opts: BeautifyOptions): Bitmap {
        val pad = opts.paddingPx.coerceAtLeast(0)
        if (pad == 0 && opts.background == BackgroundKind.NONE && opts.cornerRadiusPx <= 0f) {
            return src
        }
        val outW = src.width + pad * 2
        val outH = src.height + pad * 2
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        drawBackground(canvas, outW, outH, opts.background)

        val imageRect = RectF(pad.toFloat(), pad.toFloat(), (pad + src.width).toFloat(), (pad + src.height).toFloat())
        if (opts.shadow && pad > 0) {
            drawShadow(canvas, imageRect, opts.cornerRadiusPx)
        }
        drawRoundedImage(canvas, src, imageRect, opts.cornerRadiusPx.coerceAtLeast(0f))
        return out
    }

    private fun drawBackground(canvas: Canvas, w: Int, h: Int, kind: BackgroundKind) {
        when (kind) {
            BackgroundKind.NONE -> return
            BackgroundKind.MIDNIGHT -> fill(canvas, w, h, Color.rgb(13, 17, 23))
            BackgroundKind.CLOUD -> fill(canvas, w, h, Color.rgb(235, 240, 246))
            BackgroundKind.CARBON -> fill(canvas, w, h, Color.rgb(28, 30, 33))
            BackgroundKind.SUNSET -> gradient(canvas, w, h, Color.rgb(255, 107, 53), Color.rgb(247, 201, 72))
            BackgroundKind.OCEAN -> gradient(canvas, w, h, Color.rgb(0, 119, 182), Color.rgb(0, 180, 216))
            BackgroundKind.GRAPE -> gradient(canvas, w, h, Color.rgb(123, 47, 190), Color.rgb(224, 64, 251))
            BackgroundKind.MINT -> gradient(canvas, w, h, Color.rgb(0, 176, 155), Color.rgb(150, 201, 61))
        }
    }

    private fun fill(canvas: Canvas, w: Int, h: Int, color: Int) {
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { this.color = color })
    }

    private fun gradient(canvas: Canvas, w: Int, h: Int, from: Int, to: Int) {
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), from, to, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    /** Layered translucent rects — a cheap soft shadow without RenderScript. */
    private fun drawShadow(canvas: Canvas, rect: RectF, radius: Float) {
        val layers = 10
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in layers downTo 1) {
            val grow = i * 2.2f
            paint.color = Color.argb(((layers - i + 1) * 4).coerceAtMost(90), 0, 0, 0)
            canvas.drawRoundRect(
                rect.left - grow / 2, rect.top - grow / 3 + i * 1.2f,
                rect.right + grow / 2, rect.bottom + grow,
                radius + grow, radius + grow, paint,
            )
        }
    }

    private fun drawRoundedImage(canvas: Canvas, src: Bitmap, dst: RectF, radius: Float) {
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(dst.width() / src.width, dst.height() / src.height)
                postTranslate(dst.left, dst.top)
            })
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        }
        canvas.drawRoundRect(dst, radius, radius, paint)
    }
}
