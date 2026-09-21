package com.sharesafe.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharesafe.app.core.AutoCrop
import com.sharesafe.app.core.Beautifier
import com.sharesafe.app.core.BeautifyOptions
import com.sharesafe.app.core.CodeScanner
import com.sharesafe.app.core.ExportFormat
import com.sharesafe.app.core.Exporter
import com.sharesafe.app.core.FaceDetector
import com.sharesafe.app.core.ImageLoader
import com.sharesafe.app.core.ImageRedactor
import com.sharesafe.app.core.OcrSpan
import com.sharesafe.app.core.Prefs
import com.sharesafe.app.core.RedactRegion
import com.sharesafe.app.core.RedactStyle
import com.sharesafe.app.core.RegionKind
import com.sharesafe.app.core.SensitiveTextDetector
import com.sharesafe.app.core.SystemBars
import com.sharesafe.app.core.TextExtractor
import com.sharesafe.app.core.clampTo
import com.sharesafe.app.core.padded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, SCANNING, EDITOR, EXPORT }

enum class ScanPhase { LOADING, OCR, FACES, CODES, DONE }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var screen by mutableStateOf(Screen.HOME)
        private set

    var source by mutableStateOf<Bitmap?>(null)
        private set
    private var sourceUri: Uri? = null

    var errorMessage by mutableStateOf<String?>(null)
    var scanPhase by mutableStateOf(ScanPhase.LOADING)
    var detectFaces by mutableStateOf(true)
    var detectCodes by mutableStateOf(true)

    /** Detection stages that failed — distinct from "found nothing". */
    var failedPhases by mutableStateOf<Set<ScanPhase>>(emptySet())
        private set
    var scanProgress by mutableFloatStateOf(0f)
        private set

    /** OCR words in full-image coords — powers tap-to-redact. */
    var ocrWords by mutableStateOf<List<OcrSpan>>(emptyList())
        private set

    /** Notice shown on export when post-render verification changed regions. */
    var verifyNotice by mutableStateOf<String?>(null)
        private set

    // ---- settings (persisted) ----
    var blacklist by mutableStateOf(Prefs.blacklist(app))
        private set
    var dynamicColor by mutableStateOf(Prefs.dynamicColor(app))
        private set
    var applyToAll by mutableStateOf(Prefs.applyToAll(app))
        private set
    var hintsSeen by mutableStateOf(Prefs.hintsSeen(app))
        private set
    var exportFormat by mutableStateOf(ExportFormat.PNG)

    // ---- batch queue ----
    var queue by mutableStateOf<List<Uri>>(emptyList())
        private set
    var queuePos by mutableIntStateOf(0)
        private set
    val hasNextInQueue get() = queuePos < queue.size - 1
    val queueSize get() = queue.size

    /** A queue persisted earlier whose URIs may still be readable. */
    var resumableQueue by mutableStateOf<Pair<List<Uri>, Int>?>(Prefs.savedQueue(app))
        private set

    // ---- crop ----
    var detectedCrop by mutableStateOf<AutoCrop.CropResult?>(null)
        private set
    var cropEnabled by mutableStateOf(true)
    var cropEditMode by mutableStateOf(false)
        private set

    // ---- regions (full-image pixel coords) ----
    var regions by mutableStateOf<List<RedactRegion>>(emptyList())
        private set
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var defaultStyle by mutableStateOf(RedactStyle.PIXELATE)
    var defaultStrength by mutableFloatStateOf(1f)
    var livePreview by mutableStateOf(false)

    private val undoStack = ArrayDeque<List<RedactRegion>>()
    private val redoStack = ArrayDeque<List<RedactRegion>>()
    var undoDepth by mutableIntStateOf(0)
        private set
    var redoDepth by mutableIntStateOf(0)
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

    // ---- settings actions ----

    fun updateBlacklist(words: Set<String>) {
        blacklist = words
        Prefs.setBlacklist(getApplication(), words)
    }

    fun updateDynamicColor(on: Boolean) {
        dynamicColor = on
        Prefs.setDynamicColor(getApplication(), on)
    }

    fun updateApplyToAll(on: Boolean) {
        applyToAll = on
        Prefs.setApplyToAll(getApplication(), on)
        if (on) persistKindPreferences()
    }

    fun markHintsSeen() {
        hintsSeen = true
        Prefs.markHintsSeen(getApplication())
    }

    fun resumeSavedQueue() {
        val saved = resumableQueue ?: return
        resumableQueue = null
        Prefs.clearQueue(getApplication())
        queue = saved.first
        queuePos = saved.second
        loadImage(queue[queuePos])
    }

    fun dismissSavedQueue() {
        resumableQueue = null
        Prefs.clearQueue(getApplication())
    }

    private fun persistQueue() {
        val app = getApplication<Application>()
        if (queue.size > 1 && queuePos < queue.size) {
            Prefs.saveQueue(app, queue, queuePos)
        } else {
            Prefs.clearQueue(app)
        }
    }

    // ---- loading / detection ----

    fun loadQueue(uris: List<Uri>) {
        if (uris.isEmpty()) return
        queue = uris
        queuePos = 0
        persistQueue()
        loadImage(uris.first())
    }

    fun nextInQueue() {
        if (!hasNextInQueue) return
        // Carry over kind-enable prefs when "apply to all" is on.
        if (applyToAll) persistKindPreferences()
        queuePos += 1
        persistQueue()
        loadImage(queue[queuePos])
    }

    private fun persistKindPreferences() {
        val disabled = RegionKind.entries
            .filter { kind -> regions.any { it.kind == kind } && regions.none { it.kind == kind && it.enabled } }
            .map { it.name }
            .toSet()
        Prefs.setDisabledKinds(getApplication(), disabled)
    }

    fun loadImage(uri: Uri) {
        scanJob?.cancel()
        resetImageState()
        sourceUri = uri
        screen = Screen.SCANNING
        errorMessage = null
        exportSaved = false
        scanJob = viewModelScope.launch {
            try {
                scanPhase = ScanPhase.LOADING
                scanProgress = 0.05f
                val bitmap = withContext(Dispatchers.IO) {
                    ImageLoader.load(getApplication<Application>().contentResolver, uri)
                }
                source = bitmap
                // Higher-res copy for detectors only — small text survives downsampling.
                val detBitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        ImageLoader.loadForDetection(
                            getApplication<Application>().contentResolver, uri,
                        )
                    }.getOrNull()
                }
                val res = getApplication<Application>().resources
                detectedCrop = withContext(Dispatchers.Default) {
                    AutoCrop.detect(
                        bitmap,
                        SystemBars.statusBarHeight(res),
                        SystemBars.navigationBarHeight(res),
                    )
                }
                runDetection(detBitmap)
                detBitmap?.recycle()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorMessage = getApplication<Application>().getString(R.string.home_error_open)
                screen = Screen.HOME
            }
        }
    }

    private suspend fun runDetection(detBitmap: Bitmap?) {
        val bitmap = source ?: return
        val crop = cropRect()

        // Detection runs on the higher-res copy when available; every rect is
        // scaled back to working-bitmap space before offsetting by the crop.
        val detScale = detBitmap?.let {
            if (it.width != bitmap.width || it.height != bitmap.height) {
                bitmap.width.toFloat() / it.width
            } else 1f
        } ?: 1f
        val detSource = detBitmap ?: bitmap
        val detCrop = Rect(
            (crop.left / detScale).toInt(),
            (crop.top / detScale).toInt(),
            (crop.right / detScale).toInt(),
            (crop.bottom / detScale).toInt(),
        )
        val detWork = withContext(Dispatchers.Default) {
            if (detCrop.left == 0 && detCrop.top == 0 &&
                detCrop.width() == detSource.width && detCrop.height() == detSource.height
            ) {
                detSource
            } else {
                Bitmap.createBitmap(detSource, detCrop.left, detCrop.top, detCrop.width(), detCrop.height())
            }
        }

        val failed = mutableSetOf<ScanPhase>()
        var progressDone = 0
        fun bump() {
            progressDone++
            scanProgress = 0.15f + 0.8f * progressDone / 3f
        }

        // OCR / faces / codes run concurrently; each failure is recorded so the
        // editor can offer a retry instead of silently showing zero findings.
        val (spans, faceRects, codes) = coroutineScope {
            val textD = async(Dispatchers.Default) {
                try {
                    TextExtractor.extractSpans(detWork)
                } catch (e: Exception) {
                    failed += ScanPhase.OCR; emptyList()
                }
            }
            val faceD = async(Dispatchers.Default) {
                if (!detectFaces) return@async emptyList()
                try {
                    FaceDetector.detect(detWork)
                } catch (e: Exception) {
                    failed += ScanPhase.FACES; emptyList()
                }
            }
            val codeD = async(Dispatchers.Default) {
                if (!detectCodes) return@async emptyList()
                try {
                    CodeScanner.scan(detWork)
                } catch (e: Exception) {
                    failed += ScanPhase.CODES; emptyList()
                }
            }
            // Progress is coarse — phases finish roughly together when parallel.
            val t = textD.await().also { scanPhase = ScanPhase.OCR; bump() }
            val f = faceD.await().also { scanPhase = ScanPhase.FACES; bump() }
            val c = codeD.await().also { scanPhase = ScanPhase.CODES; bump() }
            Triple(t, f, c)
        }
        failedPhases = failed

        val textRegions = SensitiveTextDetector.detectRegions(
            spans, detWork.width, detWork.height,
        )

        val faceRegions = faceRects.map {
            RedactRegion.new(it, RegionKind.FACE, detail = "Face")
        }
        val codeRegions = codes.map {
            // QR/barcodes stay scannable through pixelation — blackout by default.
            RedactRegion.new(
                it.bounds, RegionKind.CODE,
                style = RedactStyle.BLACK,
                detail = if (it.isQr) "QR" else "Barcode",
            )
        }

        // Blacklist words from OCR spans.
        val blacklistRegions = if (blacklist.isEmpty()) emptyList() else {
            val terms = blacklist.map { it.lowercase() }
            spans.filter { span ->
                val t = span.text.lowercase()
                terms.any { term -> term.length >= 2 && t.contains(term) }
            }.map { span ->
                RedactRegion.new(
                    span.bounds.padded(0.08f, 0.18f, detWork.width, detWork.height),
                    RegionKind.SECRET, detail = "Blacklist",
                )
            }
        }

        fun toFullCoords(r: Rect): Rect = Rect(
            (r.left * detScale).toInt() + crop.left,
            (r.top * detScale).toInt() + crop.top,
            (r.right * detScale).toInt() + crop.left,
            (r.bottom * detScale).toInt() + crop.top,
        )

        ocrWords = spans.map { span ->
            span.copy(bounds = toFullCoords(span.bounds))
        }

        var all = (textRegions + faceRegions + codeRegions + blacklistRegions).map { r ->
            r.copy(rect = toFullCoords(r.rect))
        }

        // Re-apply kinds the user disabled earlier when "apply to all" is on.
        if (applyToAll) {
            val disabled = Prefs.disabledKinds(getApplication())
            if (disabled.isNotEmpty()) {
                all = all.map { r ->
                    if (r.kind.name in disabled) r.copy(enabled = false) else r
                }
            }
        }

        regions = dedupe(all)
        undoStack.clear()
        redoStack.clear()
        undoDepth = 0
        redoDepth = 0
        selectedIds = emptySet()
        scanPhase = ScanPhase.DONE
        scanProgress = 1f
        screen = Screen.EDITOR
        refreshPreview()
    }

    /** Re-runs just the failed detection stages (or all on demand). */
    fun retryDetection() {
        val uri = sourceUri ?: return
        val src = source ?: return
        scanJob = viewModelScope.launch {
            val detBitmap = withContext(Dispatchers.IO) {
                runCatching {
                    ImageLoader.loadForDetection(
                        getApplication<Application>().contentResolver, uri,
                    )
                }.getOrNull()
            }
            try {
                screen = Screen.SCANNING
                scanPhase = ScanPhase.LOADING
                runDetection(detBitmap)
            } finally {
                detBitmap?.recycle()
            }
        }
    }

    private fun dedupe(list: List<RedactRegion>): List<RedactRegion> {
        val out = mutableListOf<RedactRegion>()
        list.forEach { r ->
            val dup = out.indexOfFirst { it.kind == r.kind && it.rect == r.rect }
            if (dup < 0) out += r
        }
        return out
    }

    // ---- undo / redo ----

    private fun pushUndo() {
        if (undoStack.lastOrNull() == regions) return
        undoStack.addLast(regions)
        if (undoStack.size > 30) undoStack.removeFirst()
        undoDepth = undoStack.size
        redoStack.clear()
        redoDepth = 0
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(regions)
        redoDepth = redoStack.size
        regions = prev
        undoDepth = undoStack.size
        selectedIds = emptySet()
        refreshPreview()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(regions)
        undoDepth = undoStack.size
        regions = next
        redoDepth = redoStack.size
        selectedIds = emptySet()
        refreshPreview()
    }

    // ---- selection ----

    fun select(id: String?) {
        selectedIds = if (id == null) emptySet() else setOf(id)
    }

    fun toggleSelect(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun selectAll() {
        selectedIds = regions.map { it.id }.toSet()
    }

    fun selectAllOf(kind: RegionKind) {
        selectedIds = regions.filter { it.kind == kind }.map { it.id }.toSet()
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    fun selectedRegions(): List<RedactRegion> = regions.filter { it.id in selectedIds }

    // ---- region ops (all push undo) ----

    fun setEnabledFor(ids: Set<String>, enabled: Boolean) {
        pushUndo()
        regions = regions.map {
            if (it.id in ids) it.copy(enabled = enabled) else it
        }
        refreshPreview()
    }

    fun toggleRegion(id: String) = setEnabledFor(setOf(id), !regions.any { it.id == id && it.enabled })

    fun removeRegion(id: String) = removeRegions(setOf(id))

    fun removeRegions(ids: Set<String>) {
        pushUndo()
        regions = regions.filterNot { it.id in ids }
        selectedIds = selectedIds - ids
        refreshPreview()
    }

    fun setRegionStyle(id: String, style: RedactStyle?) =
        setStyleFor(setOf(id), style)

    fun setStyleFor(ids: Set<String>, style: RedactStyle?) {
        pushUndo()
        regions = regions.map { if (it.id in ids) it.copy(styleOverride = style) else it }
        refreshPreview()
    }

    fun setStrengthFor(ids: Set<String>, strength: Float?) {
        pushUndo()
        regions = regions.map { if (it.id in ids) it.copy(strengthOverride = strength) else it }
        refreshPreview()
    }

    fun setKindEnabled(kind: RegionKind, enabled: Boolean) {
        pushUndo()
        regions = regions.map {
            if (it.kind == kind) it.copy(enabled = enabled) else it
        }
        if (applyToAll) persistKindPreferences()
        refreshPreview()
    }

    fun applyStyleToAll(style: RedactStyle) {
        defaultStyle = style
        pushUndo()
        regions = regions.map { it.copy(styleOverride = style) }
        refreshPreview()
    }

    fun addRegion(rect: Rect, kind: RegionKind = RegionKind.MANUAL, detail: String = "") {
        val src = source ?: return
        val clamped = rect.clampTo(src.width, src.height) ?: return
        pushUndo()
        val region = RedactRegion.new(clamped, kind, detail = detail)
        regions = regions + region
        selectedIds = setOf(region.id)
        refreshPreview()
    }

    /** Tap on a detected OCR word → redact just that word. */
    fun redactWordAt(fullX: Int, fullY: Int): Boolean {
        if (hitTestRegion(fullX, fullY) != null) return false
        val span = ocrWords.firstOrNull { it.bounds.contains(fullX, fullY) } ?: return false
        addRegion(Rect(span.bounds), RegionKind.MANUAL, detail = "Word")
        return true
    }

    fun hitTestRegion(fullX: Int, fullY: Int): RedactRegion? =
        regions.lastOrNull { it.rect.contains(fullX, fullY) }

    fun duplicateSelected() {
        val src = source ?: return
        if (selectedIds.isEmpty()) return
        pushUndo()
        val copies = regions.filter { it.id in selectedIds }.map { r ->
            val w = r.rect.width(); val h = r.rect.height()
            val l = (r.rect.left + 24).coerceIn(0, src.width - w)
            val t = (r.rect.top + 24).coerceIn(0, src.height - h)
            r.copy(id = RedactRegion.new(r.rect, r.kind).id, rect = Rect(l, t, l + w, t + h))
        }
        regions = regions + copies
        selectedIds = copies.map { it.id }.toSet()
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

    fun beginGesture() = pushUndo()

    fun updateCrop(enabled: Boolean) {
        cropEnabled = enabled
        refreshPreview()
    }

    fun toggleCropEdit() {
        cropEditMode = !cropEditMode
        if (cropEditMode) cropEnabled = true
    }

    fun updateCropRect(rect: Rect) {
        val src = source ?: return
        val clamped = rect.clampTo(src.width, src.height, minSize = 32) ?: return
        detectedCrop = AutoCrop.CropResult(clamped, "manual")
        cropEnabled = true
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
        return ImageRedactor.render(cropped, shifted, defaultStyle, defaultStrength)
    }

    /**
     * Re-scans a rendered image for surviving barcodes. Any still-decodable
     * code overlapping a CODE region is upgraded to blackout — pixelation is
     * not a safe redaction for structured codes.
     * Returns the upgraded regions list (same list when nothing leaks).
     */
    suspend fun verifyCodes(list: List<RedactRegion>, renderedCrop: Bitmap): List<RedactRegion> {
        val crop = cropRect()
        val surviving = try {
            CodeScanner.scan(renderedCrop)
        } catch (e: Exception) {
            return list
        }
        if (surviving.isEmpty()) return list
        val leaky = list.filter { r ->
            r.kind == RegionKind.CODE && r.enabled &&
                (r.styleOverride ?: defaultStyle) != RedactStyle.BLACK &&
                surviving.any { code ->
                    Rect(code.bounds).apply { offset(crop.left, crop.top) }
                        .let { Rect.intersects(it, r.rect) }
                }
        }
        if (leaky.isEmpty()) return list
        val leakyIds = leaky.map { it.id }.toSet()
        verifyNotice = getApplication<Application>().getString(
            R.string.verify_notice, leaky.size,
        )
        return list.map { if (it.id in leakyIds) it.copy(styleOverride = RedactStyle.BLACK) else it }
    }

    /** Render + post-verify pass. Mutates regions if a code leaks. */
    suspend fun renderVerified(): Bitmap? {
        val redacted = renderRedacted() ?: return null
        val upgraded = verifyCodes(regions, redacted)
        if (upgraded !== regions) {
            regions = upgraded
            redacted.recycle()
            return renderRedacted()
        }
        return redacted
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
        cropEditMode = false
        screen = Screen.EXPORT
        renderFinal()
    }

    fun renderFinal() {
        finalRenderJob?.cancel()
        finalRenderJob = viewModelScope.launch(Dispatchers.Default) {
            verifyNotice = null
            val redacted = renderVerified() ?: return@launch
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
            Exporter.saveToGallery(context, bmp, exportFormat) != null
        } catch (e: Exception) {
            errorMessage = getApplication<Application>().getString(R.string.save_failed) + ": ${e.message}"
            false
        } finally {
            exporting = false
        }
    }

    suspend fun shareUri(context: android.content.Context): Uri? {
        val bmp = renderedFinal ?: return null
        return runCatching { Exporter.shareUri(context, bmp, exportFormat) }.getOrNull()
    }

    private fun resetImageState() {
        regions = emptyList()
        selectedIds = emptySet()
        detectedCrop = null
        cropEditMode = false
        renderedPreview = null
        renderedFinal = null
        exportSaved = false
        failedPhases = emptySet()
        ocrWords = emptyList()
        verifyNotice = null
        undoStack.clear()
        redoStack.clear()
        undoDepth = 0
        redoDepth = 0
    }

    fun reset() {
        scanJob?.cancel()
        source = null
        sourceUri = null
        queue = emptyList()
        queuePos = 0
        Prefs.clearQueue(getApplication())
        resetImageState()
        errorMessage = null
        screen = Screen.HOME
    }

    fun navigateTo(target: Screen) {
        if (target != Screen.EDITOR) cropEditMode = false
        screen = target
        if (target == Screen.EDITOR) refreshPreview()
    }
}
