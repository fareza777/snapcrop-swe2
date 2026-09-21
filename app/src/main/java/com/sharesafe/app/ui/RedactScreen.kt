package com.sharesafe.app.ui

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharesafe.app.MainViewModel
import com.sharesafe.app.R
import com.sharesafe.app.core.ImageRedactor
import com.sharesafe.app.core.RedactRegion
import com.sharesafe.app.core.RedactStyle
import com.sharesafe.app.ui.theme.Teal
import com.sharesafe.app.ui.theme.regionColor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private fun Offset.isFinite(): Boolean = x.isFinite() && y.isFinite()

private sealed interface GestureMode {
    data object Draw : GestureMode
    data object Transform : GestureMode
    data class Move(val regionId: String) : GestureMode
    data class Resize(val regionId: String, val corner: Int) : GestureMode
    data class CropResize(val corner: Int) : GestureMode
    data object CropMove : GestureMode
    data object CropDraw : GestureMode
    data object Idle : GestureMode
}

@Composable
fun RedactScreen(vm: MainViewModel, onDone: () -> Unit, onClose: () -> Unit) {
    val crop = vm.cropRect()
    val src = vm.source
    val cropEditing = vm.cropEditMode

    val baseBitmap = remember(src, crop, cropEditing) {
        if (src == null) null
        else if (cropEditing || (crop.left == 0 && crop.top == 0 &&
                crop.width() == src.width && crop.height() == src.height)
        ) src
        else Bitmap.createBitmap(src, crop.left, crop.top, crop.width(), crop.height())
    }

    val displayed = if (!cropEditing && vm.livePreview) {
        vm.renderedPreview ?: baseBitmap
    } else baseBitmap
    val enabledCount = vm.regions.count { it.enabled }
    val selected = vm.regions.filter { it.id in vm.selectedIds }
    val kinds = vm.regions.map { it.kind }.distinct()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // ---------- top bar ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (cropEditing) stringResource(R.string.editor_crop_title) else stringResource(R.string.editor_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    buildString {
                        if (cropEditing) append(stringResource(R.string.editor_crop_subtitle))
                        else {
                            append(
                                if (enabledCount == 1) stringResource(R.string.editor_areas_one)
                                else stringResource(R.string.editor_areas_many, enabledCount)
                            )
                            if (vm.queueSize > 1) append("  •  " + stringResource(R.string.editor_image_pos, vm.queuePos + 1, vm.queueSize))
                        }
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = vm::undo, enabled = vm.undoDepth > 0) {
                Icon(
                    Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo),
                    tint = if (vm.undoDepth > 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
            IconButton(onClick = vm::redo, enabled = vm.redoDepth > 0) {
                Icon(
                    Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo),
                    tint = if (vm.redoDepth > 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
            IconButton(
                onClick = {
                    if (vm.selectedIds.size == vm.regions.size) vm.clearSelection()
                    else vm.selectAll()
                },
                enabled = vm.regions.isNotEmpty() && !cropEditing,
            ) {
                Icon(
                    Icons.Outlined.SelectAll, stringResource(R.string.select_all),
                    tint = if (vm.selectedIds.isNotEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.toggleLivePreview(!vm.livePreview) }, enabled = !cropEditing) {
                Icon(
                    if (vm.livePreview) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    stringResource(R.string.preview_toggle),
                    tint = if (vm.livePreview) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert, stringResource(R.string.more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (vm.cropEnabled) stringResource(R.string.menu_autocrop_on) else stringResource(R.string.menu_autocrop_off)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.CropFree, null,
                                tint = if (vm.cropEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        onClick = { vm.updateCrop(!vm.cropEnabled); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (cropEditing) stringResource(R.string.menu_done_crop) else stringResource(R.string.menu_edit_crop)) },
                        leadingIcon = { Icon(Icons.Outlined.Crop, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { vm.toggleCropEdit(); menuOpen = false },
                    )
                    if (vm.failedPhases.isNotEmpty() || vm.ocrWords.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_rescan)) },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { vm.retryDetection(); menuOpen = false },
                        )
                    }
                }
            }
        }

        // ---------- degraded-scan banner ----------
        if (vm.failedPhases.isNotEmpty() && !cropEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                    .clickable { vm.retryDetection() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Warning, null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(R.string.scan_failed),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        // ---------- canvas ----------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0A0D11)),
        ) {
            if (displayed != null) {
                RedactCanvas(
                    vm = vm,
                    bitmap = displayed,
                    cropOriginX = if (cropEditing) 0 else crop.left,
                    cropOriginY = if (cropEditing) 0 else crop.top,
                    cropEdit = cropEditing,
                )
            }
        }

        // ---------- bottom controls ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            if (selected.isNotEmpty() && !cropEditing) {
                SelectionBar(
                    selected = selected,
                    defaultStyle = vm.defaultStyle,
                    defaultStrength = vm.defaultStrength,
                    onStyle = { vm.setStyleFor(vm.selectedIds, it) },
                    onStrength = { vm.setStrengthFor(vm.selectedIds, it) },
                    onToggle = { vm.setEnabledFor(vm.selectedIds, selected.any { !it.enabled }) },
                    onDuplicate = vm::duplicateSelected,
                    onDelete = { vm.removeRegions(vm.selectedIds) },
                    onClose = vm::clearSelection,
                )
                Spacer(Modifier.height(10.dp))
            } else if (kinds.isNotEmpty() && !cropEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    kinds.forEach { kind ->
                        val group = vm.regions.filter { it.kind == kind }
                        val allOn = group.all { it.enabled }
                        val allSelected = group.all { it.id in vm.selectedIds }
                        FilterChip(
                            selected = allSelected || allOn,
                            onClick = { vm.selectAllOf(kind) },
                            label = {
                                Text(
                                    "${kind.label} ${group.size}" +
                                        if (!allOn) " (off)" else "",
                                    fontSize = 12.sp,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = regionColor(kind).copy(alpha = 0.22f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedLabelColor = regionColor(kind),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = allSelected || allOn,
                                borderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderColor = regionColor(kind),
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (!cropEditing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        RedactStyle.entries.forEachIndexed { i, style ->
                            SegmentedButton(
                                selected = vm.defaultStyle == style,
                                onClick = { vm.defaultStyle = style },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = i,
                                    count = RedactStyle.entries.size,
                                ),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    activeContentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(style.label, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (selected.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.effect),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(52.dp),
                        )
                        Slider(
                            value = vm.defaultStrength,
                            onValueChange = { vm.defaultStrength = it; vm.refreshPreview() },
                            valueRange = ImageRedactor.MIN_STRENGTH..ImageRedactor.MAX_STRENGTH,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Teal,
                                thumbColor = Teal,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%.1fx".format(vm.defaultStrength),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(38.dp),
                        )
                    }
                }
                Text(
                    stringResource(R.string.editor_hint),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    stringResource(R.string.editor_crop_hint),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = stringResource(R.string.editor_done),
                onClick = onDone,
            )
            Spacer(Modifier.height(14.dp))
        }
    }

    // One-time gesture coach marks.
    if (!vm.hintsSeen) {
        CoachMarkOverlay(onDismiss = vm::markHintsSeen)
    }
}

@Composable
private fun CoachMarkOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .padding(22.dp),
        ) {
            Text(
                stringResource(R.string.coach_title),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            listOf(
                stringResource(R.string.coach_tap),
                stringResource(R.string.coach_longpress),
                stringResource(R.string.coach_move),
                stringResource(R.string.coach_zoom),
            ).forEach {
                Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("•", color = Teal, fontWeight = FontWeight.Bold)
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            GradientButton(text = stringResource(R.string.coach_gotit), onClick = onDismiss)
        }
    }
}

@Composable
private fun SelectionBar(
    selected: List<RedactRegion>,
    defaultStyle: RedactStyle,
    defaultStrength: Float,
    onStyle: (RedactStyle?) -> Unit,
    onStrength: (Float) -> Unit,
    onToggle: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val single = selected.singleOrNull()
    val kindColor = if (single != null) regionColor(single.kind) else MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, kindColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PillLabel(
                single?.kind?.label?.uppercase() ?: stringResource(R.string.selected_count, selected.size),
                kindColor,
            )
            Spacer(Modifier.width(2.dp))
            RedactStyle.entries.forEach { style ->
                val active = (single?.styleOverride ?: defaultStyle) == style
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else Color.Transparent
                        )
                        .clickable { onStyle(style) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        when (style) {
                            RedactStyle.BLUR -> "B"
                            RedactStyle.PIXELATE -> "Px"
                            RedactStyle.BLACK -> "■"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggle, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (selected.all { it.enabled }) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    stringResource(R.string.toggle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.ContentCopy, stringResource(R.string.duplicate),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.Delete, stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.Close, stringResource(R.string.deselect),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        // For multi-selections show the shared override if uniform, else the
        // session default — the slider still writes one value to all selected.
        val shownStrength = if (single != null) {
            single.strengthOverride ?: defaultStrength
        } else {
            selected.map { it.strengthOverride }.distinct().singleOrNull() ?: defaultStrength
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.effect),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp),
            )
            Slider(
                value = shownStrength,
                onValueChange = onStrength,
                valueRange = ImageRedactor.MIN_STRENGTH..ImageRedactor.MAX_STRENGTH,
                colors = SliderDefaults.colors(
                    activeTrackColor = Teal,
                    thumbColor = Teal,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                "%.1fx".format(shownStrength),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(38.dp),
            )
        }
        Text(
            stringResource(R.string.editor_sel_hint),
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun RedactCanvas(
    vm: MainViewModel,
    bitmap: Bitmap,
    cropOriginX: Int,
    cropOriginY: Int,
    cropEdit: Boolean,
) {
    var userScale by remember { mutableStateOf(1f) }
    var userPan by remember { mutableStateOf(Offset.Zero) }
    var dragRect by remember { mutableStateOf<Rect?>(null) }
    var cropDraft by remember { mutableStateOf<Rect?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()

    fun fits(): Pair<Offset, Float> {
        if (imgW <= 0f || imgH <= 0f || canvasSize.width <= 0f || canvasSize.height <= 0f) {
            return Offset.Zero to 1f
        }
        val s = min(canvasSize.width / imgW, canvasSize.height / imgH)
        val ox = (canvasSize.width - imgW * s) / 2f
        val oy = (canvasSize.height - imgH * s) / 2f
        return Offset(ox, oy) to s
    }

    fun origin(): Offset = fits().first + userPan

    fun totalScale(): Float = fits().second * userScale

    fun toImage(canvasPt: Offset): Offset {
        val s = totalScale()
        val o = origin()
        if (!s.isFinite() || s <= 0f || !o.isFinite()) return Offset.Zero
        val img = Offset((canvasPt.x - o.x) / s, (canvasPt.y - o.y) / s)
        return if (img.isFinite()) img else Offset.Zero
    }

    fun toCanvas(imgPt: Offset): Offset {
        val s = totalScale()
        val o = origin()
        if (!s.isFinite() || !o.isFinite() || !imgPt.isFinite()) return Offset.Zero
        return Offset(o.x + imgPt.x * s, o.y + imgPt.y * s)
    }

    fun hitRegion(imgPt: Offset): RedactRegion? {
        if (!imgPt.x.isFinite() || !imgPt.y.isFinite()) return null
        val fullX = imgPt.x + cropOriginX
        val fullY = imgPt.y + cropOriginY
        return vm.regions.lastOrNull {
            it.rect.contains(fullX.roundToInt(), fullY.roundToInt())
        }
    }

    fun clampPan() {
        val s = totalScale()
        if (!s.isFinite() || s <= 0f) { userPan = Offset.Zero; userScale = 1f; return }
        val (fitOrigin, _) = fits()
        val drawW = imgW * s
        val drawH = imgH * s
        val margin = 120f
        val ox = (origin().x).coerceIn(-drawW + margin, canvasSize.width - margin)
        val oy = (origin().y).coerceIn(-drawH + margin, canvasSize.height - margin)
        val next = Offset(ox, oy) - fitOrigin
        userPan = if (next.isFinite()) next else Offset.Zero
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(bitmap, cropOriginX, cropOriginY, cropEdit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var mode: GestureMode = GestureMode.Idle
                    var drawStart = Offset.Zero
                    var lastImg = Offset.Zero
                    var prevCentroid = Offset.Zero
                    var prevSpan = 0f
                    var moved = false
                    var undoPushed = false
                    var gestureRegionStart: Rect? = null
                    var gestureCropStart: Rect? = null

                    val downImg = toImage(down.position)

                    if (cropEdit) {
                        val crop = vm.cropRect()
                        val s = totalScale()
                        val slopImg = 30f / s
                        val corners = listOf(
                            Offset(crop.left.toFloat(), crop.top.toFloat()),
                            Offset(crop.right.toFloat(), crop.top.toFloat()),
                            Offset(crop.right.toFloat(), crop.bottom.toFloat()),
                            Offset(crop.left.toFloat(), crop.bottom.toFloat()),
                        )
                        val cornerHit = corners.indexOfFirst {
                            kotlin.math.abs(it.x - downImg.x) < slopImg &&
                                kotlin.math.abs(it.y - downImg.y) < slopImg
                        }
                        mode = when {
                            cornerHit >= 0 -> {
                                gestureCropStart = Rect(crop)
                                GestureMode.CropResize(cornerHit)
                            }
                            crop.contains(downImg.x.roundToInt(), downImg.y.roundToInt()) -> {
                                gestureCropStart = Rect(crop)
                                lastImg = downImg
                                GestureMode.CropMove
                            }
                            else -> {
                                drawStart = downImg
                                GestureMode.CropDraw
                            }
                        }
                    } else {
                        val hit = hitRegion(downImg)
                        val singleSelected = vm.regions.firstOrNull {
                            it.id == vm.selectedIds.singleOrNull()
                        }

                        val handle = singleSelected?.let { sel ->
                            val rect = Rect(sel.rect).apply { offset(-cropOriginX, -cropOriginY) }
                            val s = totalScale()
                            val slopImg = 28f / s
                            val corners = listOf(
                                Offset(rect.left.toFloat(), rect.top.toFloat()),
                                Offset(rect.right.toFloat(), rect.top.toFloat()),
                                Offset(rect.right.toFloat(), rect.bottom.toFloat()),
                                Offset(rect.left.toFloat(), rect.bottom.toFloat()),
                            )
                            corners.indexOfFirst {
                                kotlin.math.abs(it.x - downImg.x) < slopImg &&
                                    kotlin.math.abs(it.y - downImg.y) < slopImg
                            }.takeIf { it >= 0 }
                        }

                        mode = when {
                            handle != null -> {
                                vm.beginGesture()
                                gestureRegionStart = Rect(singleSelected.rect)
                                GestureMode.Resize(singleSelected.id, handle)
                            }
                            hit != null -> {
                                // tap = select, long-press = add/remove from selection, drag = move.
                                // Returns null on release; non-null change once the system
                                // long-press timeout fires.
                                val lp = awaitLongPressOrCancellation(down.id)
                                if (lp != null) {
                                    vm.toggleSelect(hit.id)
                                    GestureMode.Idle
                                } else {
                                    gestureRegionStart = Rect(hit.rect)
                                    lastImg = downImg
                                    GestureMode.Move(hit.id)
                                }
                            }
                            else -> {
                                drawStart = downImg
                                GestureMode.Draw
                            }
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2 && mode != GestureMode.Transform) {
                            mode = GestureMode.Transform
                            prevCentroid = Offset.Zero
                            prevSpan = 0f
                            dragRect = null
                            cropDraft = null
                        }

                        when (val m = mode) {
                            GestureMode.Transform -> {
                                // Only recompute with real touches — an empty `pressed`
                                // list (all fingers up) would emit a NaN centroid and
                                // poison pan/scale state, crashing later hit tests.
                                if (pressed.isNotEmpty()) {
                                    val pts = pressed.map { it.position }
                                    val centroid = Offset(
                                        pts.map { it.x }.average().toFloat(),
                                        pts.map { it.y }.average().toFloat(),
                                    )
                                    val span = if (pts.size >= 2) {
                                        pts.map {
                                            sqrt(
                                                (it.x - centroid.x) * (it.x - centroid.x) +
                                                    (it.y - centroid.y) * (it.y - centroid.y)
                                            )
                                        }.average().toFloat()
                                    } else 0f

                                    if (centroid.isFinite() && span.isFinite()) {
                                        if (prevSpan > 0 && span > 0) {
                                            val ratio = span / prevSpan
                                            val base = fits().second
                                            val newScale = (userScale * ratio).coerceIn(1f, 8f)
                                            val s = base * userScale
                                            val o = origin()
                                            if (s > 0 && s.isFinite() && o.isFinite()) {
                                                val anchor = Offset(
                                                    (centroid.x - o.x) / s,
                                                    (centroid.y - o.y) / s,
                                                )
                                                val newS = base * newScale
                                                userScale = newScale
                                                userPan = Offset(
                                                    centroid.x - anchor.x * newS,
                                                    centroid.y - anchor.y * newS,
                                                ) - fits().first
                                            }
                                        }
                                        if (prevCentroid != Offset.Zero) {
                                            userPan += centroid - prevCentroid
                                        }
                                        prevCentroid = centroid
                                        prevSpan = span
                                    }
                                    clampPan()
                                    moved = true
                                }
                            }
                            is GestureMode.Move -> {
                                val p = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull() ?: break
                                val curImg = toImage(p.position)
                                if (p.positionChange() != Offset.Zero) {
                                    moved = true
                                    val dx = (curImg.x - lastImg.x).roundToInt()
                                    val dy = (curImg.y - lastImg.y).roundToInt()
                                    if (dx != 0 || dy != 0) {
                                        if (!undoPushed) { vm.beginGesture(); undoPushed = true }
                                        vm.moveRegion(m.regionId, dx, dy)
                                        lastImg = Offset(curImg.x - dx, curImg.y - dy)
                                    }
                                }
                            }
                            is GestureMode.Resize -> {
                                val p = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull() ?: break
                                val curImg = toImage(p.position)
                                moved = true
                                val start = gestureRegionStart
                                if (start != null) {
                                    val full = Offset(
                                        curImg.x + cropOriginX,
                                        curImg.y + cropOriginY,
                                    )
                                    val nr = Rect(start)
                                    when (m.corner) {
                                        0 -> { nr.left = full.x.toInt(); nr.top = full.y.toInt() }
                                        1 -> { nr.right = full.x.toInt(); nr.top = full.y.toInt() }
                                        2 -> { nr.right = full.x.toInt(); nr.bottom = full.y.toInt() }
                                        3 -> { nr.left = full.x.toInt(); nr.bottom = full.y.toInt() }
                                    }
                                    vm.resizeRegion(m.regionId, nr)
                                }
                            }
                            is GestureMode.CropResize -> {
                                val p = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.first()
                                val curImg = toImage(p.position)
                                moved = true
                                val start = gestureCropStart
                                if (start != null) {
                                    val nr = Rect(start)
                                    when (m.corner) {
                                        0 -> { nr.left = curImg.x.toInt(); nr.top = curImg.y.toInt() }
                                        1 -> { nr.right = curImg.x.toInt(); nr.top = curImg.y.toInt() }
                                        2 -> { nr.right = curImg.x.toInt(); nr.bottom = curImg.y.toInt() }
                                        3 -> { nr.left = curImg.x.toInt(); nr.bottom = curImg.y.toInt() }
                                    }
                                    cropDraft = nr
                                }
                            }
                            GestureMode.CropMove -> {
                                val p = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull() ?: break
                                val curImg = toImage(p.position)
                                if (p.positionChange() != Offset.Zero) {
                                    moved = true
                                    val start = gestureCropStart
                                    if (start != null) {
                                        val dx = (curImg.x - lastImg.x).roundToInt()
                                        val dy = (curImg.y - lastImg.y).roundToInt()
                                        if (dx != 0 || dy != 0) {
                                            val src = vm.source
                                            if (src != null) {
                                                val w = start.width(); val h = start.height()
                                                val l = (start.left + dx)
                                                    .coerceIn(0, src.width - w)
                                                val t = (start.top + dy)
                                                    .coerceIn(0, src.height - h)
                                                cropDraft = Rect(l, t, l + w, t + h)
                                                gestureCropStart = Rect(cropDraft!!)
                                            }
                                            lastImg = curImg
                                        }
                                    }
                                }
                            }
                            GestureMode.CropDraw -> {
                                val p = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull() ?: break
                                val curImg = toImage(p.position)
                                if (p.positionChange() != Offset.Zero) moved = true
                                cropDraft = Rect(
                                    min(drawStart.x, curImg.x).roundToInt(),
                                    min(drawStart.y, curImg.y).roundToInt(),
                                    kotlin.math.max(drawStart.x, curImg.x).roundToInt(),
                                    kotlin.math.max(drawStart.y, curImg.y).roundToInt(),
                                )
                            }
                            GestureMode.Draw -> {
                                val p = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull() ?: break
                                val curImg = toImage(p.position)
                                if (p.positionChange() != Offset.Zero) moved = true
                                dragRect = Rect(
                                    min(drawStart.x, curImg.x).roundToInt(),
                                    min(drawStart.y, curImg.y).roundToInt(),
                                    kotlin.math.max(drawStart.x, curImg.x).roundToInt(),
                                    kotlin.math.max(drawStart.y, curImg.y).roundToInt(),
                                )
                            }
                            GestureMode.Idle -> {}
                        }

                        event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }

                        if (event.changes.all { it.changedToUp() || !it.pressed }) {
                            when (val m = mode) {
                                GestureMode.Draw -> {
                                    val r = dragRect
                                    if (r != null && moved && r.width() >= 6 && r.height() >= 6) {
                                        vm.addRegion(
                                            Rect(r).apply { offset(cropOriginX, cropOriginY) }
                                        )
                                    } else if (!moved) {
                                        val fullX = (downImg.x + cropOriginX).roundToInt()
                                        val fullY = (downImg.y + cropOriginY).roundToInt()
                                        if (!vm.redactWordAt(fullX, fullY)) vm.clearSelection()
                                    }
                                    dragRect = null
                                }
                                is GestureMode.Move -> {
                                    if (!moved) vm.select(m.regionId) else vm.refreshPreview()
                                }
                                is GestureMode.Resize -> {
                                    vm.refreshPreview()
                                }
                                is GestureMode.CropResize,
                                GestureMode.CropMove,
                                GestureMode.CropDraw -> {
                                    val d = cropDraft
                                    if (d != null && d.width() >= 32 && d.height() >= 32) {
                                        vm.updateCropRect(d)
                                    }
                                    cropDraft = null
                                }
                                else -> {}
                            }
                            break
                        }
                    }
                }
            },
    ) {
        canvasSize = size

        val s = totalScale()
        val o = origin()

        drawImage(
            image = bitmap.asImageBitmap(),
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
            dstSize = IntSize((imgW * s).roundToInt(), (imgH * s).roundToInt()),
        )

        if (cropEdit) {
            val crop = cropDraft ?: vm.cropRect()
            val tl = toCanvas(Offset(crop.left.toFloat(), crop.top.toFloat()))
            val br = toCanvas(Offset(crop.right.toFloat(), crop.bottom.toFloat()))

            // dim outside the crop
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset.Zero,
                size = Size(canvasSize.width, tl.y.coerceAtLeast(0f)),
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, br.y),
                size = Size(canvasSize.width, (canvasSize.height - br.y).coerceAtLeast(0f)),
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, tl.y.coerceAtLeast(0f)),
                size = Size(tl.x.coerceAtLeast(0f), (br.y - tl.y).coerceAtLeast(0f)),
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(br.x, tl.y.coerceAtLeast(0f)),
                size = Size((canvasSize.width - br.x).coerceAtLeast(0f), (br.y - tl.y).coerceAtLeast(0f)),
            )
            drawRect(
                color = Teal,
                topLeft = tl,
                size = Size(br.x - tl.x, br.y - tl.y),
                style = Stroke(width = 2.dp.toPx()),
            )
            val rad = 6.dp.toPx()
            listOf(tl, Offset(br.x, tl.y), br, Offset(tl.x, br.y)).forEach { c ->
                drawCircle(Color.White, rad * 1.6f, c)
                drawCircle(Teal, rad, c)
            }
        } else {
            // region overlays
            vm.regions.forEach { r ->
                val rel = Rect(r.rect).apply { offset(-cropOriginX, -cropOriginY) }
                val tl = toCanvas(Offset(rel.left.toFloat(), rel.top.toFloat()))
                val br = toCanvas(Offset(rel.right.toFloat(), rel.bottom.toFloat()))
                val color = regionColor(r.kind)
                val isSelected = r.id in vm.selectedIds
                val showHandles = isSelected && vm.selectedIds.size == 1

                if (!r.enabled) {
                    drawRect(
                        color = color.copy(alpha = 0.06f),
                        topLeft = tl,
                        size = Size(br.x - tl.x, br.y - tl.y),
                    )
                    drawRect(
                        color = color.copy(alpha = 0.35f),
                        topLeft = tl,
                        size = Size(br.x - tl.x, br.y - tl.y),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                        ),
                    )
                } else {
                    val fillAlpha = when (r.styleOverride ?: vm.defaultStyle) {
                        RedactStyle.BLACK -> 0.82f
                        RedactStyle.BLUR -> 0.30f
                        RedactStyle.PIXELATE -> 0.34f
                    }
                    val fillColor = if ((r.styleOverride ?: vm.defaultStyle) == RedactStyle.BLACK) {
                        Color.Black.copy(alpha = fillAlpha)
                    } else {
                        color.copy(alpha = fillAlpha)
                    }
                    drawRect(color = fillColor, topLeft = tl, size = Size(br.x - tl.x, br.y - tl.y))
                    drawRect(
                        color = color.copy(alpha = if (isSelected) 1f else 0.75f),
                        topLeft = tl,
                        size = Size(br.x - tl.x, br.y - tl.y),
                        style = Stroke(width = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx()),
                    )
                    // kind badge
                    drawContext.canvas.nativeCanvas.apply {
                        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).also {
                            it.textSize = 11.dp.toPx()
                            it.color = android.graphics.Color.WHITE
                            it.textAlign = android.graphics.Paint.Align.CENTER
                            it.isFakeBoldText = true
                        }
                        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).also {
                            it.color = android.graphics.Color.argb(
                                220,
                                (color.red * 255).toInt().coerceIn(0, 255),
                                (color.green * 255).toInt().coerceIn(0, 255),
                                (color.blue * 255).toInt().coerceIn(0, 255),
                            )
                        }
                        val badgeW = 20.dp.toPx()
                        val badgeH = 16.dp.toPx()
                        drawRoundRect(
                            tl.x, tl.y - badgeH - 2,
                            tl.x + badgeW, tl.y - 2,
                            5.dp.toPx(), 5.dp.toPx(), bgPaint,
                        )
                        drawText(
                            r.kind.badge,
                            tl.x + badgeW / 2, tl.y - 6.dp.toPx(),
                            textPaint,
                        )
                    }
                }

                if (showHandles && r.enabled) {
                    val rad = 5.dp.toPx()
                    listOf(tl, Offset(br.x, tl.y), br, Offset(tl.x, br.y)).forEach { c ->
                        drawCircle(Color.White, rad * 1.6f, c)
                        drawCircle(color, rad, c)
                    }
                }
            }
        }

        // in-progress draw rect (region or crop)
        val pending = dragRect
        if (pending != null) {
            val tl = toCanvas(Offset(pending.left.toFloat(), pending.top.toFloat()))
            val br = toCanvas(Offset(pending.right.toFloat(), pending.bottom.toFloat()))
            drawRect(
                color = Color(0xFFFFD43B).copy(alpha = 0.25f),
                topLeft = tl,
                size = Size(br.x - tl.x, br.y - tl.y),
            )
            drawRect(
                color = Color(0xFFFFD43B),
                topLeft = tl,
                size = Size(br.x - tl.x, br.y - tl.y),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                ),
            )
        }
    }
}
