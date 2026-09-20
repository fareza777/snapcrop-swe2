package com.sharesafe.app.core

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** OCR text span with source-image geometry. */
data class OcrSpan(val text: String, val bounds: Rect, val separatorAfter: Char = ' ')

object TextExtractor {

    /**
     * Returns the smallest OCR elements with geometry so separate secrets in one
     * block stay independently redactable. Falls back to lines, then blocks.
     */
    suspend fun extractSpans(bitmap: Bitmap): List<OcrSpan> {
        if (bitmap.isRecycled) return emptyList()
        return suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result.toSpans())
                }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
                .addOnCanceledListener { if (cont.isActive) cont.cancel() }
                .addOnCompleteListener { recognizer.close() }
        }
    }

    private fun Text.toSpans(): List<OcrSpan> =
        textBlocks.flatMap { block ->
            val elements = block.lines.flatMap { line ->
                line.elements.mapNotNull { el ->
                    el.boundingBox?.let { OcrSpan(el.text, it) }
                }
            }
            if (elements.isNotEmpty()) {
                elements.mapIndexed { i, span ->
                    span.copy(separatorAfter = if (i == elements.lastIndex) '\n' else ' ')
                }
            } else {
                val lines = block.lines.mapNotNull { line ->
                    line.boundingBox?.let { OcrSpan(line.text, it, '\n') }
                }
                if (lines.isNotEmpty()) lines else listOfNotNull(
                    block.boundingBox?.let { OcrSpan(block.text, it, '\n') }
                )
            }
        }
}
