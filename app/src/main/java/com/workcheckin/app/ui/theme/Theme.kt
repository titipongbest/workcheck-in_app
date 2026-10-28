package com.workcheckin.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFFF7F3F0)
val Brown = Color(0xFF6B4237)
val DarkBrown = Color(0xFF3B2622)
val Rust = Color(0xFF8A3A00)
val Peach = Color(0xFFFFE5DC)
val CheckGreen = Color(0xFF00C878)
val MissRed = Color(0xFFD9534F)
val Muted = Color(0xFF8E8682)
val LightGrey = Color(0xFFE4E1DF)

private val colors = lightColorScheme(
    primary = Brown,
    onPrimary = Color.White,
    primaryContainer = Peach,
    onPrimaryContainer = Rust,
    secondary = Rust,
    background = Background,
    onBackground = DarkBrown,
    surface = Background,
    onSurface = DarkBrown,
    surfaceVariant = Color.White,
    surfaceContainer = Color(0xFFF1EBE7),
    surfaceContainerLow = Color.White,
    outline = Color(0xFFB7AFAB),
)

@Composable
fun WorkCheckInTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
