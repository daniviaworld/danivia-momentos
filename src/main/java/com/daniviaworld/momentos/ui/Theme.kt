package com.daniviaworld.momentos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFFF8A3D)
val Violet = Color(0xFF7C5CFF)
val Night = Color(0xFF111325)
val NightCard = Color(0xFF1D2036)

private val colors = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF1A0E05),
    secondary = Violet,
    onSecondary = Color.White,
    background = Night,
    onBackground = Color(0xFFF2F0FA),
    surface = NightCard,
    onSurface = Color(0xFFF2F0FA),
    surfaceVariant = Color(0xFF2A2E48),
    onSurfaceVariant = Color(0xFFCFCDE3),
    surfaceContainer = Color(0xFF1F2238),
    surfaceContainerHigh = Color(0xFF2A2E48),
    surfaceContainerHighest = Color(0xFF33375A),
    error = Color(0xFFFF6B6B),
)

@Composable
fun MomentosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
