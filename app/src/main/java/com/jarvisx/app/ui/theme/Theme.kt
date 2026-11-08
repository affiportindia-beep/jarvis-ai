package com.jarvisx.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** JARVIS X palette: near-black, cool cyan core, violet secondary. Original identity. */
object Jx {
    val Background = Color(0xFF05070D)
    val Surface = Color(0xFF0C111C)
    val SurfaceHigh = Color(0xFF131A29)
    val Outline = Color(0xFF243049)
    val Accent = Color(0xFF3DDCFF)
    val AccentDim = Color(0xFF1C6C82)
    val Violet = Color(0xFF7C8CFF)
    val Text = Color(0xFFE6EDF7)
    val TextDim = Color(0xFF8C9AB3)
    val Good = Color(0xFF4ADE80)
    val Warn = Color(0xFFFBBF24)
    val Bad = Color(0xFFF87171)
}

private val scheme = darkColorScheme(
    primary = Jx.Accent,
    onPrimary = Color(0xFF00141A),
    secondary = Jx.Violet,
    onSecondary = Color(0xFF0B0E25),
    background = Jx.Background,
    onBackground = Jx.Text,
    surface = Jx.Surface,
    onSurface = Jx.Text,
    surfaceVariant = Jx.SurfaceHigh,
    onSurfaceVariant = Jx.TextDim,
    outline = Jx.Outline,
    error = Jx.Bad,
)

private val typography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 1.5.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.5.sp, fontFamily = FontFamily.Monospace),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
