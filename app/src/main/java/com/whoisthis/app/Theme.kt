package com.whoisthis.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

object Palette {
    val Background = Color(0xFF090E11)
    val Surface = Color(0xFF131B20)
    val Raised = Color(0xFF1B262C)
    val Border = Color(0xFF2A383E)
    val Text = Color(0xFFEAF1F3)
    val Muted = Color(0xFF9DADB5)
    val Accent = Color(0xFFB8F36D)
    val Ink = Color(0xFF172208)
    val Warning = Color(0xFFFFCA85)
    val Object = Color(0xFF7BC7ED)
    val Error = Color(0xFFFFB4AB)
    val Camera = Color(0xFF05090B)
    val Hud = Color(0xEB101A1F)
    val Shade = Color(0xA6090E11)
    val Clear = Color.Transparent
    val Interface = FontFamily.SansSerif
    val Telemetry = FontFamily.Monospace
}

@Composable
fun TaggerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Palette.Ink,
            primaryContainer = Palette.Raised,
            onPrimaryContainer = Palette.Accent,
            secondary = Palette.Accent,
            onSecondary = Palette.Ink,
            secondaryContainer = Palette.Raised,
            onSecondaryContainer = Palette.Text,
            tertiary = Palette.Warning,
            onTertiary = Palette.Ink,
            tertiaryContainer = Palette.Raised,
            onTertiaryContainer = Palette.Text,
            background = Palette.Background,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.Raised,
            onSurfaceVariant = Palette.Muted,
            outline = Palette.Border,
            outlineVariant = Palette.Border,
            surfaceDim = Palette.Background,
            surfaceBright = Palette.Raised,
            surfaceContainerLowest = Palette.Background,
            surfaceContainerLow = Palette.Surface,
            surfaceContainer = Palette.Surface,
            surfaceContainerHigh = Palette.Raised,
            surfaceContainerHighest = Palette.Raised,
            error = Palette.Error,
        ),
        typography = Typography().let { defaults ->
            defaults.copy(
                headlineLarge = defaults.headlineLarge.copy(fontFamily = Palette.Interface),
                headlineMedium = defaults.headlineMedium.copy(fontFamily = Palette.Interface),
                titleLarge = defaults.titleLarge.copy(fontFamily = Palette.Interface),
                bodyLarge = defaults.bodyLarge.copy(fontFamily = Palette.Interface),
                bodyMedium = defaults.bodyMedium.copy(fontFamily = Palette.Interface),
            )
        },
        content = content,
    )
}
