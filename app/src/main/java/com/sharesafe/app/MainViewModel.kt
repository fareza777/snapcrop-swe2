package com.sharesafe.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharesafe.app.core.AutoCrop
import com.sharesafe.app.core.Beautifier
import com.sharesafe.app.core.BeautifyOptions
import com.sharesafe.app.core.CodeScanner
import com.sharesafe.app.core.Exporter
import com.sharesafe.app.core.FaceDetector
import com.sharesafe.app.core.ImageLoader
import com.sharesafe.app.core.ImageRedactor
import com.sharesafe.app.core.RedactRegion
import com.sharesafe.app.core.RedactStyle
import com.sharesafe.app.core.RegionKind
import com.sharesafe.app.core.SensitiveTextDetector
import com.sharesafe.app.core.SystemBars
import com.sharesafe.app.core.TextExtractor
import com.sharesafe.app.core.clampTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, SCANNING, EDITOR, EXPORT }

enum class ScanPhase { LOADING, OCR, FACES, CODES, DONE }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var screen by mutableStateOf(Screen.HOME)
        private set

    var source by mutableStateOf<Bitmap?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
    var scanPhase by mutableStateOf(ScanPhase.LOADING)
    var detectFaces by mutableStateOf(true)
    var detectCodes by mutableStateOf(true)

    // ---- crop ----
    var detectedCrop by mutableStateOf<AutoCrop.CropResult?>(null)
        private set
    var cropEnabled by mutableStateOf(true)

    // ---- regions (full-image pixel coords) ----
    var regions by mutableStateOf<List<RedactRegion>>(emptyList())
        private set
    var selectedId by mutableStateOf<String?>(null)
    var defaultStyle by mutableStateOf(RedactStyle.PIXELATE)
    var livePreview by mutableStateOf(false)

    private val undoStack = ArrayDeque<List<RedactRegion>>()
    var undoDepth by mutableIntStateOf(0)
        private set

    // ---- export ----
    var beautify by mutableStateOf(
        BeautifyOptions(paddingPx = 96, cornerRadiusPx = 36f, background = com.sharesafe.app.core.BackgroundKind.MIDNIGHT),
    )
    var exporting by mutableStateOf(false)
    var exportSaved by mutableStateOf(false)

    private var previewJob: Job? = null
    var renderedPreview by mutableStateOf<Bitmap?>(null)
        private set

    private var finalRenderJob: Job? = null
    var renderedFinal by mutableStateOf<Bitmap?>(null)
        private set

    private var scanJob: Job? = null

    fun cropRect(): Rect {
        val src = source ?: return Rect()
        val detected = detectedCrop
        return if (cropEnabled && detected != null && detected.method != "full") {
            Rect(detected.rect)
        } else {
            Rect(0, 0, src.width, src.height)
        }
    }

    fun loadImage(uri: Uri) {
        scanJob?.cancel()
        screen = Screen.SCANNING
        errorMessage = null
        exportSaved = false
        scanJob = viewModelScope.launch {
            try {
                scanPhase = ScanPhase.LOADING
                val bitmap = withContext(Dispatchers.IO) {
                    ImageLoader.load(getApplication<Application>().contentResolver, uri)
                }
                source = bitmap
                val res = getApplication<Application>().resources
                detectedCrop = withContext(Dispatchers.Default) {
                    AutoCrop.detect(
                        bitmap,
                        SystemBars.statusBarHeight(res),
                        SystemBars.navigationBarHeight(res),
                    )
                }
                runDetection()
            } catch (e: Exception) {
                errorMessage = "Could not open this image."
                screen = Screen.HOME
            }
        }
    }

    private suspend fun runDetection() {
        val bitmap = source ?: return
        val crop = cropRect()
        val workBitmap = withContext(Dispatchers.Default) {
            if (crop.left == 0 && crop.top == 0 &&
                crop.width() == bitmap.width && crop.height() == bitmap.height
            ) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
            }
        }

        scanPhase = ScanPhase.OCR
        val textRegions = withContext(Dispatchers.Default) {
            runCatching {
                SensitiveTextDetector.detectRegions(
                    TextExtractor.extractSpans(workBitmap),
                    workBitmap.width,
                    workBitmap.height,
                )
            }.getOrDefault(emptyList())
        }

        val faceRegions = if (detectFaces) {
            scanPhase = ScanPhase.FACES
            withContext(Dispatchers.Default) {
                FaceDetector.detect(workBitmap).map {
                    RedactRegion.new(it, RegionKind.FACE, detail = "Face")
                }
            }
        } else emptyList()

        val codeRegions = if (detectCodes) {
            scanPhase = ScanPhase.CODES
            withContext(Dispatchers.Default) {
                CodeScanner.scan(workBitmap).map {
                    // QR/barcodes stay scannable through pixelation — blackout by default.
                    RedactRegion.new(
                        it.bounds,
                        RegionKind.CODE,
                        style = RedactStyle.BLACK,
                        detail = if (it.isQr) "QR" else "Barcode",
                    )
                }
            }
        } else emptyList()

        val ox = crop.left
        val oy = crop.top
        val all = (textRegions + faceRegions + codeRegions).map { r ->
            r.copy(rect = Rect(r.rect).apply { offset(ox, oy) })
        }
        regions = dedupe(all)
        undoStack.clear()
        undoDepth = 0
        selectedId = null
        scanPhase = ScanPhase.DONE
        screen = Screen.EDITOR
        refreshPreview()
    }

    private fun dedupe(list: List<RedactRegion>): List<RedactRegion> {
        val out = mutableListOf<RedactRegion>()
        list.forEach { r ->
            val dup = out.indexOfFirst { it.kind == r.kind && it.rect == r.rect }
            if (dup < 0) out += r
        }
        return out
    }

    // ---- region ops (all push undo) ----

    private fun pushUndo() {
        if (undoStack.lastOrNull() == regions) return
        undoStack.addLast(regions)
        if (undoStack.size > 30) undoStack.removeFirst()
        undoDepth = undoStack.size
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        regions = prev
        undoDepth = undoStack.size
        selectedId = null
        refreshPreview()
    }

    fun toggleRegion(id: String) {
        pushUndo()
        regions = regions.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        refreshPreview()
    }

    fun removeRegion(id: String) {
        pushUndo()
        regions = regions.filterNot { it.id == id }
        if (selectedId == id) selectedId = null
        refreshPreview()
    }

    fun setRegionStyle(id: String, style: RedactStyle?) {
        pushUndo()
        regions = regions.map { if (it.id == id) it.copy(styleOverride = style) else it }
        refreshPreview()
    }

    fun setKindEnabled(kind: RegionKind, enabled: Boolean) {
        pushUndo()
        regions = regions.map {
            if (it.kind == kind) it.copy(enabled = enabled) else it
        }
        refreshPreview()
    }

    fun applyStyleToAll(style: RedactStyle) {
        defaultStyle = style
        pushUndo()
        regions = regions.map { it.copy(styleOverride = style) }
        refreshPreview()
    }

    fun addRegion(rect: Rect) {
        val src = source ?: return
        val clamped = rect.clampTo(src.width, src.height) ?: return
        pushUndo()
        val region = RedactRegion.new(clamped, RegionKind.MANUAL)
        regions = regions + region
        selectedId = region.id
        refreshPreview()
    }

    fun moveRegion(id: String, dx: Int, dy: Int) {
        val src = source ?: return
        regions = regions.map { r ->
            if (r.id != id) r else {
                val w = r.rect.width(); val h = r.rect.height()
                val l = (r.rect.left + dx).coerceIn(0, src.width - w)
                val t = (r.rect.top + dy).coerceIn(0, src.height - h)
                r.copy(rect = Rect(l, t, l + w, t + h))
            }
        }
    }

    fun resizeRegion(id: String, newRect: Rect) {
        val src = source ?: return
        regions = regions.map { r ->
            if (r.id != id) r
            else r.copy(rect = newRect.clampTo(src.width, src.height) ?: r.rect)
        }
    }

    fun commitGesture() {
        // call once when a move/resize gesture ends — snapshot for undo there,
        // not per frame, so the stack stays meaningful
    }

    fun beginGesture() = pushUndo()

    fun updateCrop(enabled: Boolean) {
        cropEnabled = enabled
        refreshPreview()
    }

    // ---- rendering ----

    /** Redacted bitmap in crop space, regions translated. */
    fun renderRedacted(): Bitmap? {
        val src = source ?: return null
        val crop = cropRect()
        val cropped = Bitmap.createBitmap(src, crop.left, crop.top, crop.width(), crop.height())
        val shifted = regions.map {
            it.copy(rect = Rect(it.rect).apply { offset(-crop.left, -crop.top) })
        }
        return ImageRedactor.render(cropped, shifted, defaultStyle)
    }

    fun refreshPreview() {
        if (!livePreview) return
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            renderedPreview = renderRedacted()
        }
    }

    fun toggleLivePreview(on: Boolean) {
        livePreview = on
        if (on) refreshPreview() else renderedPreview = null
    }

    fun goExport() {
        screen = Screen.EXPORT
        renderFinal()
    }

    fun renderFinal() {
        finalRenderJob?.cancel()
        finalRenderJob = viewModelScope.launch(Dispatchers.Default) {
            val redacted = renderRedacted() ?: return@launch
            renderedFinal = Beautifier.render(redacted, beautify)
        }
    }

    fun updateBeautify(opts: BeautifyOptions) {
        beautify = opts
        renderFinal()
    }

    suspend fun saveFinal(context: android.content.Context): Boolean {
        val bmp = renderedFinal ?: return false
        exporting = true
        return try {
            Exporter.saveToGallery(context, bmp) != null
        } catch (e: Exception) {
            errorMessage = "Save failed: ${e.message}"
            false
        } finally {
            exporting = false
        }
    }

    suspend fun shareUri(context: android.content.Context): Uri? {
        val bmp = renderedFinal ?: return null
        return runCatching { Exporter.shareUri(context, bmp) }.getOrNull()
    }

    fun reset() {
        scanJob?.cancel()
        source = null
        regions = emptyList()
        selectedId = null
        detectedCrop = null
        renderedPreview = null
        renderedFinal = null
        exportSaved = false
        errorMessage = null
        undoStack.clear()
        undoDepth = 0
        screen = Screen.HOME
    }

    fun navigateTo(target: Screen) {
        screen = target
        if (target == Screen.EDITOR) refreshPreview()
    }

    fun select(id: String?) {
        selectedId = id
    }
}
