package io.github.conorryan.timerwidget.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Fixed brand palette, built on the widget's own green (`widget_background`)
 * with one amber accent. The app always uses it, light system theme or not,
 * so it matches the home-screen widget.
 */
object Brand {
    val Ground = Color(0xFF0A2A20)
    val Surface = Color(0xFF0F3B2E)
    val Raised = Color(0xFF164A3A)
    val Line = Color(0xFF1E5443)
    val LineStrong = Color(0xFF2C6A56)
    val Text = Color(0xFFECE6F0)
    val TextSoft = Color(0xFFC9D8D1)
    val TextMuted = Color(0xFFA9C2B7)
    val WheelNear = Color(0xFF8FB3A4)
    val WheelFar = Color(0xFF5E8F7D)
    val Amber = Color(0xFFF2B45A)
    val OnAmber = Color(0xFF2A1A04)
    val OnAmberMuted = Color(0xFF5A3D0E)
    val Danger = Color(0xFFF4A28C)
}

private val BrandColors = darkColorScheme(
    primary = Brand.Amber,
    onPrimary = Brand.OnAmber,
    primaryContainer = Brand.Amber,
    onPrimaryContainer = Brand.OnAmber,
    secondary = Brand.Amber,
    onSecondary = Brand.OnAmber,
    secondaryContainer = Brand.Raised,
    onSecondaryContainer = Brand.Text,
    background = Brand.Ground,
    onBackground = Brand.Text,
    surface = Brand.Ground,
    onSurface = Brand.Text,
    surfaceVariant = Brand.Raised,
    onSurfaceVariant = Brand.TextMuted,
    surfaceContainerLowest = Brand.Ground,
    surfaceContainerLow = Brand.Surface,
    surfaceContainer = Brand.Surface,
    surfaceContainerHigh = Brand.Surface,
    surfaceContainerHighest = Brand.Raised,
    outline = Brand.LineStrong,
    outlineVariant = Brand.Line,
    error = Brand.Danger,
    onError = Brand.OnAmber,
)

@Composable
fun TimerWidgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BrandColors, content = content)
}
