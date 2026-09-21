package com.sharesafe.app.core

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FaceDetector {

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.08f)
                .build()
        )
    }

    suspend fun detect(bitmap: Bitmap): List<Rect> {
        if (bitmap.isRecycled) return emptyList()
        return suspendCancellableCoroutine { cont ->
            detector.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { faces ->
                    if (cont.isActive) {
                        cont.resume(
                            faces.map { face ->
                                val b = face.boundingBox
                                Rect(b.left, b.top, b.right, b.bottom)
                                    .padded(0.15f, 0.15f, bitmap.width, bitmap.height)
                            }
                        )
                    }
                }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }
    }
}

data class ScannedCode(val rawValue: String, val bounds: Rect, val isQr: Boolean)

object CodeScanner {

    private val scanner by lazy { BarcodeScanning.getClient() }

    suspend fun scan(bitmap: Bitmap): List<ScannedCode> {
        if (bitmap.isRecycled) return emptyList()
        return suspendCancellableCoroutine { cont ->
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { barcodes ->
                    if (cont.isActive) {
                        cont.resume(
                            barcodes.mapNotNull { barcode ->
                                val bounds = barcode.boundingBox ?: return@mapNotNull null
                                val raw = barcode.rawValue ?: return@mapNotNull null
                                ScannedCode(
                                    raw,
                                    bounds.padded(0.06f, 0.06f, bitmap.width, bitmap.height),
                                    barcode.format == Barcode.FORMAT_QR_CODE,
                                )
                            }
                        )
                    }
                }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }
    }
}
