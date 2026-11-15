package com.daniviaworld.momentos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFFF8A3D)
val Violet = Color(0xFF7C5CFF)
val Night = Color(0xFF0E0F1A)
val NightCard = Color(0xFF1A1C2E)

private val colors = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF1A0E05),
    secondary = Violet,
    onSecondary = Color.White,
    background = Night,
    onBackground = Color(0xFFEDEBF5),
    surface = NightCard,
    onSurface = Color(0xFFEDEBF5),
    surfaceVariant = Color(0xFF26283D),
    onSurfaceVariant = Color(0xFFB9B7CC),
    surfaceContainer = Color(0xFF1F2135),
    surfaceContainerHigh = Color(0xFF26283D),
    error = Color(0xFFFF6B6B),
)

@Composable
fun MomentosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
