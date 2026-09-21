package com.sharesafe.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.FaceRetouchingOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharesafe.app.MainViewModel
import com.sharesafe.app.R
import com.sharesafe.app.core.CustomRule
import com.sharesafe.app.core.ScreenshotWatcher
import com.sharesafe.app.ui.theme.AccentBrush
import com.sharesafe.app.ui.theme.Cyan
import com.sharesafe.app.ui.theme.Teal

@Composable
fun HomeScreen(vm: MainViewModel, error: String?, onPick: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // Ambient glow behind the hero — premium depth cue, no assets needed.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Teal.copy(alpha = 0.14f),
                            Teal.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        radius = 1100f,
                    )
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(AccentBrush),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "S",
                            color = Color(0xFF08110E),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                        )
                    }
                    Text(
                        "ShareSafe",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.2.sp,
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        Icons.Outlined.Settings, stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(30.dp))

            // Hero shield — layered glass tiles + halo rings.
            Box(contentAlignment = Alignment.Center) {
                listOf(172.dp to 0.05f, 138.dp to 0.08f).forEach { (d, a) ->
                    Box(
                        modifier = Modifier
                            .size(d)
                            .clip(RoundedCornerShape(d / 2.6f))
                            .border(1.dp, Teal.copy(alpha = a), RoundedCornerShape(d / 2.6f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Teal.copy(alpha = 0.22f),
                                    Cyan.copy(alpha = 0.10f),
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                            )
                        )
                        .border(
                            1.2.dp,
                            Brush.linearGradient(
                                listOf(Teal.copy(alpha = 0.7f), Cyan.copy(alpha = 0.25f))
                            ),
                            RoundedCornerShape(30.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = Teal,
                        modifier = Modifier.size(54.dp),
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Text(
                "ShareSafe",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                style = TextStyle(brush = AccentBrush),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.home_tagline),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }

            vm.pendingScreenshot?.let { _ ->
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, Teal.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .clickable { vm.openPendingScreenshot() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Screenshot, null, tint = Teal, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.new_shot_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.new_shot_sub),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.dismissPendingScreenshot() }) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }

            vm.resumableQueue?.let { (uris, pos) ->
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, Teal.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickable { vm.resumeSavedQueue() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.History, null, tint = Teal, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.resume_batch),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (uris.size - pos > 1) stringResource(R.string.resume_batch_sub, uris.size - pos)
                            else stringResource(R.string.resume_batch_one),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.dismissSavedQueue() }) { Text(stringResource(R.string.dismiss)) }
                }
            }

            Spacer(Modifier.height(34.dp))
            GradientButton(text = stringResource(R.string.home_pick), onClick = onPick)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.home_pick_hint),
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(36.dp))

            // Feature grid — glass tiles instead of bare chips.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureTile(Icons.Outlined.TextFields, stringResource(R.string.chip_ocr), Modifier.weight(1f))
                    FeatureTile(Icons.Outlined.FaceRetouchingOff, stringResource(R.string.chip_face), Modifier.weight(1f))
                    FeatureTile(Icons.Outlined.QrCodeScanner, stringResource(R.string.chip_qr), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureTile(Icons.Outlined.CropFree, stringResource(R.string.chip_crop), Modifier.weight(1f))
                    FeatureTile(Icons.Outlined.AutoFixHigh, stringResource(R.string.chip_beautify), Modifier.weight(1f))
                    // Privacy badge tile balances the grid.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Teal.copy(alpha = 0.14f),
                                        Cyan.copy(alpha = 0.07f),
                                    )
                                )
                            )
                            .border(1.dp, Teal.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
                            .aspectRatio(1f)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Teal),
                            )
                            Spacer(Modifier.height(9.dp))
                            Text(
                                stringResource(R.string.home_private),
                                fontSize = 10.5.sp,
                                lineHeight = 13.sp,
                                textAlign = TextAlign.Center,
                                color = Teal,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showSettings) {
        SettingsSheet(vm = vm, onClose = { showSettings = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(vm: MainViewModel, onClose: () -> Unit) {
    var newWord by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 30.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.settings),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.settings_always_redact))
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_always_redact_sub),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.settings_add_word), fontSize = 13.sp) },
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        val w = newWord.trim()
                        if (w.isNotEmpty()) {
                            vm.updateBlacklist(vm.blacklist + w)
                            newWord = ""
                        }
                    },
                ) { Text(stringResource(R.string.add)) }
            }
            Spacer(Modifier.height(8.dp))
            vm.blacklist.forEach { word ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        word,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = { vm.updateBlacklist(vm.blacklist - word) }) {
                        Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.settings_rules))
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_rules_sub),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            var ruleLabel by remember { mutableStateOf("") }
            var rulePattern by remember { mutableStateOf("") }
            var ruleError by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = ruleLabel,
                onValueChange = { ruleLabel = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.rule_label_ph), fontSize = 13.sp) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rulePattern,
                    onValueChange = { rulePattern = it; ruleError = false },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.rule_pattern_ph), fontSize = 13.sp) },
                    singleLine = true,
                    isError = ruleError,
                )
                TextButton(
                    onClick = {
                        val rule = CustomRule(ruleLabel.trim(), rulePattern.trim())
                        if (rule.toRegex() != null && ruleLabel.isNotBlank() && rulePattern.isNotBlank()) {
                            vm.updateCustomRules(vm.customRules + rule)
                            ruleLabel = ""; rulePattern = ""
                        } else {
                            ruleError = true
                        }
                    },
                ) { Text(stringResource(R.string.add)) }
            }
            if (ruleError) {
                Text(
                    stringResource(R.string.rule_invalid),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            vm.customRules.forEach { rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            rule.label.uppercase(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            rule.pattern,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        vm.updateCustomRules(vm.customRules.filter { it != rule })
                    }) {
                        Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.settings_watch))
            val context = androidx.compose.ui.platform.LocalContext.current
            val watchLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted -> vm.updateWatchScreenshots(granted) }
            SettingSwitch(
                label = stringResource(R.string.settings_watch_shots),
                sub = stringResource(R.string.settings_watch_shots_sub),
                checked = vm.watchScreenshots && ScreenshotWatcher.hasPermission(context),
            ) { on ->
                if (on && !ScreenshotWatcher.hasPermission(context)) {
                    watchLauncher.launch(ScreenshotWatcher.requiredPermission())
                } else {
                    vm.updateWatchScreenshots(on)
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.settings_batch))
            SettingSwitch(
                label = stringResource(R.string.settings_apply_all),
                sub = stringResource(R.string.settings_apply_all_sub),
                checked = vm.applyToAll,
            ) { vm.updateApplyToAll(it) }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Spacer(Modifier.height(6.dp))
                SectionLabel(stringResource(R.string.settings_appearance))
                SettingSwitch(
                    label = stringResource(R.string.settings_dynamic_color),
                    sub = stringResource(R.string.settings_dynamic_color_sub),
                    checked = vm.dynamicColor,
                ) { vm.updateDynamicColor(it) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingSwitch(label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(sub, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FeatureTile(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Teal.copy(alpha = 0.16f),
                                Cyan.copy(alpha = 0.10f),
                            )
                        )
                    )
                    .border(1.dp, Teal.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                label,
                fontSize = 10.5.sp,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
