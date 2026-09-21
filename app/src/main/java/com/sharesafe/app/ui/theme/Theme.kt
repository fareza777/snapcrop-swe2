package com.sharesafe.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF2DE1B0)
val Cyan = Color(0xFF31C8FF)
val Ink = Color(0xFF0B0F14)
val InkSurface = Color(0xFF141B24)
val InkSurfaceHigh = Color(0xFF1B2531)
val InkBorder = Color(0xFF26303D)
val OnInk = Color(0xFFE8EEF4)
val OnInkDim = Color(0xFF9AA9B8)

val PaperBg = Color(0xFFF2F5F9)
val PaperSurface = Color(0xFFFFFFFF)
val PaperBorder = Color(0xFFDDE4EC)
val OnPaper = Color(0xFF0E1620)
val OnPaperDim = Color(0xFF5D6E7E)
val TealDeep = Color(0xFF0B9E7E)

val AccentBrush = Brush.linearGradient(listOf(Teal, Cyan))

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Ink,
    secondary = Cyan,
    onSecondary = Ink,
    tertiary = Color(0xFF8B7CFF),
    background = Ink,
    onBackground = OnInk,
    surface = InkSurface,
    onSurface = OnInk,
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = OnInkDim,
    outline = InkBorder,
    error = Color(0xFFFF6B6B),
    onError = Ink,
)

private val LightColors = lightColorScheme(
    primary = TealDeep,
    onPrimary = Color.White,
    secondary = Color(0xFF0B8FC4),
    onSecondary = Color.White,
    tertiary = Color(0xFF6C5CE7),
    background = PaperBg,
    onBackground = OnPaper,
    surface = PaperSurface,
    onSurface = OnPaper,
    surfaceVariant = Color(0xFFE8EEF4),
    onSurfaceVariant = OnPaperDim,
    outline = PaperBorder,
    error = Color(0xFFC0392B),
    onError = Color.White,
)

@Composable
fun ShareSafeTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}

/** Stable per-kind accent colours for region outlines + chips. */
fun regionColor(kind: com.sharesafe.app.core.RegionKind): Color = when (kind) {
    com.sharesafe.app.core.RegionKind.EMAIL -> Color(0xFF4CC3FF)
    com.sharesafe.app.core.RegionKind.PHONE -> Color(0xFF4ADE80)
    com.sharesafe.app.core.RegionKind.CARD -> Color(0xFFFFA94D)
    com.sharesafe.app.core.RegionKind.NUMBER -> Color(0xFF2DE1B0)
    com.sharesafe.app.core.RegionKind.SECRET -> Color(0xFFFF6B6B)
    com.sharesafe.app.core.RegionKind.NETWORK -> Color(0xFF67E8F9)
    com.sharesafe.app.core.RegionKind.ADDRESS -> Color(0xFFD0BFFF)
    com.sharesafe.app.core.RegionKind.CODE -> Color(0xFFF472D0)
    com.sharesafe.app.core.RegionKind.FACE -> Color(0xFFB197FC)
    com.sharesafe.app.core.RegionKind.MANUAL -> Color(0xFFFFD43B)
}
