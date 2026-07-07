package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorPalette = darkColorScheme(
    background = AlightBg,
    surface = PanelBlue,
    surfaceVariant = GraphBlue,
    primary = SelectedGreen,
    secondary = ControlTeal,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun MotionStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorPalette,
        typography = Typography,
        content = content
    )
}
