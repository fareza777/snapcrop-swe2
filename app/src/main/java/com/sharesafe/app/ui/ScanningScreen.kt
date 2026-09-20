package com.sharesafe.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharesafe.app.ScanPhase
import com.sharesafe.app.ui.theme.Cyan
import com.sharesafe.app.ui.theme.Teal

@Composable
fun ScanningScreen(phase: ScanPhase, onCancel: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "p",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .alpha(pulse)
                .clip(RoundedCornerShape(28.dp))
                .background(Teal.copy(alpha = 0.14f))
                .border(1.dp, Teal.copy(alpha = 0.4f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.TextFields,
                contentDescription = null,
                tint = Teal,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Scanning screenshot",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (phase) {
                ScanPhase.LOADING -> "Loading image…"
                ScanPhase.OCR -> "Reading text (on-device OCR)…"
                ScanPhase.FACES -> "Detecting faces…"
                ScanPhase.CODES -> "Scanning QR & barcodes…"
                ScanPhase.DONE -> "Done"
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            StageDot(Icons.Outlined.TextFields, "Text", phase.ordinal > ScanPhase.OCR.ordinal)
            StageDot(Icons.Outlined.Face, "Faces", phase.ordinal > ScanPhase.FACES.ordinal)
            StageDot(Icons.Outlined.QrCode, "Codes", phase.ordinal > ScanPhase.CODES.ordinal)
        }
        Spacer(Modifier.height(44.dp))
        TextButton(onClick = onCancel) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StageDot(icon: ImageVector, label: String, done: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (done) Teal.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    1.dp,
                    if (done) Teal else MaterialTheme.colorScheme.outline,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (done) Teal else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = if (done) Teal else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (done) {
            Text("✓", fontSize = 10.sp, color = Cyan)
        } else {
            Spacer(Modifier.height(10.dp))
        }
    }
}
