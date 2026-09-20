package com.sharesafe.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharesafe.app.MainViewModel
import com.sharesafe.app.core.BackgroundKind
import com.sharesafe.app.core.Exporter
import com.sharesafe.app.ui.theme.AccentBrush
import com.sharesafe.app.ui.theme.Teal
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ExportScreen(vm: MainViewModel, onBack: () -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareBusy by remember { mutableStateOf(false) }
    var saveDone by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Preview",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Redaction is permanent in the export",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0A0D11))
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                val final = vm.renderedFinal
                if (final != null) {
                    Image(
                        bitmap = final.asImageBitmap(),
                        contentDescription = "Redacted preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                final.width.toFloat() / final.height.toFloat()
                            ),
                    )
                } else {
                    Text(
                        "Rendering…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(40.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("Beautify")
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackgroundKind.entries.forEach { kind ->
                    BackgroundSwatch(
                        kind = kind,
                        selected = vm.beautify.background == kind,
                        onClick = { vm.updateBeautify(vm.beautify.copy(background = kind)) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SliderRow(
                label = "Padding",
                value = vm.beautify.paddingPx.toFloat(),
                range = 0f..160f,
                enabled = vm.beautify.background != BackgroundKind.NONE,
            ) { vm.updateBeautify(vm.beautify.copy(paddingPx = it.roundToInt())) }

            SliderRow(
                label = "Corners",
                value = vm.beautify.cornerRadiusPx,
                range = 0f..96f,
                enabled = vm.beautify.background != BackgroundKind.NONE,
            ) { vm.updateBeautify(vm.beautify.copy(cornerRadiusPx = it)) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Shadow",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.Switch(
                    checked = vm.beautify.shadow,
                    onCheckedChange = { vm.updateBeautify(vm.beautify.copy(shadow = it)) },
                    enabled = vm.beautify.background != BackgroundKind.NONE,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            GradientButton(
                text = if (shareBusy) "Preparing…" else "Safe Share",
                onClick = {
                    shareBusy = true
                    scope.launch {
                        val uri = vm.shareUri(context)
                        shareBusy = false
                        if (uri != null) {
                            context.startActivity(
                                android.content.Intent.createChooser(
                                    Exporter.shareIntent(uri),
                                    "Share redacted image",
                                )
                            )
                        }
                    }
                },
                enabled = vm.renderedFinal != null && !shareBusy,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable(enabled = vm.renderedFinal != null && !vm.exporting) {
                            scope.launch {
                                if (vm.saveFinal(context)) saveDone = true
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            if (saveDone || vm.exportSaved) Icons.Outlined.Check
                            else Icons.Outlined.SaveAlt,
                            null,
                            tint = if (saveDone) Teal else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            when {
                                vm.exporting -> "Saving…"
                                saveDone -> "Saved"
                                else -> "Save to gallery"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (saveDone) Teal else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable(onClick = onFinish)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        "Done",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun BackgroundSwatch(kind: BackgroundKind, selected: Boolean, onClick: () -> Unit) {
    val brush: Brush? = when (kind) {
        BackgroundKind.NONE -> null
        BackgroundKind.MIDNIGHT -> Brush.linearGradient(listOf(Color(0xFF0D1117), Color(0xFF0D1117)))
        BackgroundKind.CLOUD -> Brush.linearGradient(listOf(Color(0xFFEBF0F6), Color(0xFFEBF0F6)))
        BackgroundKind.CARBON -> Brush.linearGradient(listOf(Color(0xFF1C1E21), Color(0xFF1C1E21)))
        BackgroundKind.SUNSET -> Brush.linearGradient(listOf(Color(0xFFFF6B35), Color(0xFFF7C948)))
        BackgroundKind.OCEAN -> Brush.linearGradient(listOf(Color(0xFF0077B6), Color(0xFF00B4D8)))
        BackgroundKind.GRAPE -> Brush.linearGradient(listOf(Color(0xFF7B2FBE), Color(0xFFE040FB)))
        BackgroundKind.MINT -> Brush.linearGradient(listOf(Color(0xFF00B09B), Color(0xFF96C93D)))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (brush != null) Modifier.background(brush)
                    else Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF2A2A2A), Color(0xFF2A2A2A)
                            )
                        )
                    )
                )
                .then(
                    if (kind == BackgroundKind.NONE) {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(14.dp),
                        )
                    } else Modifier
                )
                .border(
                    width = if (selected) 2.5.dp else 0.dp,
                    color = if (selected) Teal else Color.Transparent,
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (kind == BackgroundKind.NONE) {
                Text("∅", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
            }
            if (selected && kind != BackgroundKind.NONE) {
                Icon(
                    Icons.Outlined.Check, null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            kind.label,
            fontSize = 10.5.sp,
            color = if (selected) Teal else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(64.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                activeTrackColor = Teal,
                thumbColor = Teal,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${value.roundToInt()}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
    }
}
