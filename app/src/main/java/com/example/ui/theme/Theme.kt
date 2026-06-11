package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CyberBlack,
    primaryContainer = ElectricBlue,
    onPrimaryContainer = CyberBlack,
    secondary = ElectricBlue,
    onSecondary = CyberBlack,
    tertiary = WarningAmber,
    onTertiary = CyberBlack,
    background = CyberBlack,
    onBackground = LightBackground,
    surface = CyberCharcoal,
    onSurface = LightBackground,
    surfaceVariant = CyberCardBorder,
    onSurfaceVariant = MutedSlate,
    error = DangerRed,
    onError = CyberBlack
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = LightSurface,
    primaryContainer = NeonCyan,
    onPrimaryContainer = CyberBlack,
    secondary = ElectricBlue,
    onSecondary = LightSurface,
    tertiary = WarningAmber,
    background = LightBackground,
    onBackground = CyberBlack,
    surface = LightSurface,
    onSurface = CyberBlack,
    surfaceVariant = MutedSlate,
    error = DangerRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
