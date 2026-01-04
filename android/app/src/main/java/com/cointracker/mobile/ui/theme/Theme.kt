package com.cointracker.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = WebPrimaryDark,
    secondary = WebSuccessDark,
    error = WebDangerDark,
    background = Color.Transparent, // Transparent to show gradient
    surface = Color(0xFF1A1D23).copy(alpha = 0.6f), // Glassy dark surface
    onPrimary = Color.Black,
    onBackground = TextDark,
    onSurface = TextDark
)

private val LightColors = lightColorScheme(
    primary = WebPrimary,
    secondary = WebSuccess,
    error = WebDanger,
    background = Color.Transparent, // Transparent to show gradient
    surface = Color(0xFFFFFFFF).copy(alpha = 0.6f), // Glassy light surface
    onPrimary = Color.White,
    onBackground = TextLight,
    onSurface = TextLight
)

@Composable
fun CoinTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography, // Ensure Type.kt exists or remove if using default
        content = content
    )
}