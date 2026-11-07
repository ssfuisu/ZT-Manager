package com.ztmanager.ui.theme

import android.graphics.Color as AColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.ztmanager.data.Prefs

data class ZtColors(
    val isDark: Boolean,
    val folder: Color,
    val secondaryText: Color,
    val divider: Color,
    val toolbar: Color,
    val pathBar: Color,
    val selection: Color,
    val activePaneMarker: Color,
    val gutter: Color,
    val gutterText: Color
)

private val darkZt = ZtColors(
    isDark = true,
    folder = Color(0xFFFFC34D),
    secondaryText = Color(0xFF9AA3AD),
    divider = Color(0xFF30353D),
    toolbar = Color(0xFF262A31),
    pathBar = Color(0xFF20242A),
    selection = Color(0x334DA3FF),
    activePaneMarker = Color(0xFF4DA3FF),
    gutter = Color(0xFF1A1D22),
    gutterText = Color(0xFF6B737D)
)

private val blackZt = darkZt.copy(
    toolbar = Color(0xFF0E0E0E),
    pathBar = Color(0xFF070707),
    divider = Color(0xFF1E1E1E),
    gutter = Color(0xFF050505)
)

private val lightZt = ZtColors(
    isDark = false,
    folder = Color(0xFFF2A60C),
    secondaryText = Color(0xFF69717B),
    divider = Color(0xFFE1E4E8),
    toolbar = Color(0xFFFFFFFF),
    pathBar = Color(0xFFEDEFF2),
    selection = Color(0x261E6FD9),
    activePaneMarker = Color(0xFF1E6FD9),
    gutter = Color(0xFFF0F2F5),
    gutterText = Color(0xFF9AA0A8)
)

private val darkScheme = darkColorScheme(
    primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF00213F),
    primaryContainer = Color(0xFF1F3A5A),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFFFC34D),
    onSecondary = Color(0xFF3A2A00),
    background = Color(0xFF1C1F24),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF1C1F24),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF2C313A),
    onSurfaceVariant = Color(0xFFB8C0C9),
    surfaceContainer = Color(0xFF23272E),
    surfaceContainerHigh = Color(0xFF2A2F37),
    surfaceContainerHighest = Color(0xFF323842),
    surfaceContainerLow = Color(0xFF20242A),
    surfaceContainerLowest = Color(0xFF16191D),
    outline = Color(0xFF4A515B),
    outlineVariant = Color(0xFF353B44),
    error = Color(0xFFFF6B6B)
)

private val blackScheme = darkScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color(0xFF0E0E0E),
    surfaceContainerHigh = Color(0xFF151515),
    surfaceContainerHighest = Color(0xFF1C1C1C),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainerLowest = Color.Black
)

private val lightScheme = lightColorScheme(
    primary = Color(0xFF1E6FD9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E5FB),
    onPrimaryContainer = Color(0xFF0A2C57),
    secondary = Color(0xFFF2A60C),
    onSecondary = Color.White,
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF1D2025),
    surface = Color(0xFFF5F6F8),
    onSurface = Color(0xFF1D2025),
    surfaceVariant = Color(0xFFE6E9ED),
    onSurfaceVariant = Color(0xFF4A515B),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF7F8FA),
    surfaceContainerHighest = Color(0xFFEEF0F3),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    outline = Color(0xFFB4BAC2),
    outlineVariant = Color(0xFFDDE1E6),
    error = Color(0xFFD32F2F)
)

/** Accent colors as (dark theme, light theme) pairs. */
val ACCENTS = listOf(
    Color(0xFF4DA3FF) to Color(0xFF1E6FD9),
    Color(0xFF4CC38A) to Color(0xFF1B8A5A),
    Color(0xFFFFA24D) to Color(0xFFD9700E),
    Color(0xFFB18CFF) to Color(0xFF6A3FD1),
    Color(0xFFFF6B6B) to Color(0xFFC62828),
    Color(0xFF3ECFCF) to Color(0xFF00838F),
    Color(0xFFF06292) to Color(0xFFC2185B)
)

val LocalZtColors = staticCompositionLocalOf { darkZt }

object ZtTheme {
    val colors: ZtColors
        @Composable get() = LocalZtColors.current
}

@Composable
fun isAppInDarkTheme(): Boolean = when (Prefs.themeMode) {
    Prefs.THEME_LIGHT -> false
    Prefs.THEME_DARK, Prefs.THEME_BLACK -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun ZtTheme(content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme()
    val black = Prefs.themeMode == Prefs.THEME_BLACK
    val accentPair = ACCENTS[Prefs.accent.coerceIn(0, ACCENTS.lastIndex)]
    val accent = if (dark) accentPair.first else accentPair.second
    val baseScheme = when {
        black -> blackScheme
        dark -> darkScheme
        else -> lightScheme
    }
    val scheme = baseScheme.copy(
        primary = accent,
        primaryContainer = accent.copy(alpha = if (dark) 0.30f else 0.18f),
        surfaceTint = accent
    )
    val baseZt = when {
        black -> blackZt
        dark -> darkZt
        else -> lightZt
    }
    val zt = baseZt.copy(
        activePaneMarker = accent,
        selection = accent.copy(alpha = if (dark) 0.20f else 0.15f)
    )
    val context = LocalContext.current
    LaunchedEffect(dark) {
        val activity = context as? ComponentActivity ?: return@LaunchedEffect
        val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
        else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    val base = Typography()
    val typography = base.copy(
        titleLarge = base.titleLarge.copy(fontSize = 20.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    )
    CompositionLocalProvider(LocalZtColors provides zt) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
