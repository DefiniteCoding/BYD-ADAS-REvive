package com.definitecoding.bydadasrevive.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ok = Color(0xFF3DDC97)
val Warn = Color(0xFFFFC857)
val Bad = Color(0xFFFF6B6B)

private val ReviveColors = darkColorScheme(
    primary = Color(0xFF4CC2FF),
    onPrimary = Color(0xFF00121F),
    secondary = Color(0xFF8A93A6),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6E9EF),
    surface = Color(0xFF171B22),
    onSurface = Color(0xFFE6E9EF),
    surfaceVariant = Color(0xFF212733),
    onSurfaceVariant = Color(0xFFB6BECC),
    error = Bad,
)

@Composable
fun ReviveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ReviveColors, content = content)
}
