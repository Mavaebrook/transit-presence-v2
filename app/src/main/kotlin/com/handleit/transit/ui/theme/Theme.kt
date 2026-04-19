package com.handleit.transit.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFF00D4FF),
    onPrimary        = Color(0xFF000000),
    secondary        = Color(0xFF00FF9F),
    onSecondary      = Color(0xFF000000),
    tertiary         = Color(0xFFFFD600),
    background       = Color(0xFF0A0E1A),
    onBackground     = Color(0xFFC8D8F0),
    surface          = Color(0xFF0F1628),
    onSurface        = Color(0xFFC8D8F0),
    surfaceVariant   = Color(0xFF141E35),
    onSurfaceVariant = Color(0xFF7A8AAA),
    outline          = Color(0xFF1A2545),
    error            = Color(0xFFFF3366),
)

@Composable
fun TransitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
