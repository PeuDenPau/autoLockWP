package com.lockcanvas.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF3DDC84),
    secondary = androidx.compose.ui.graphics.Color(0xFF4285F4),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFF4081),
    surface = androidx.compose.ui.graphics.Color(0xFF101014),
    background = androidx.compose.ui.graphics.Color(0xFF0A0A0C),
)

@Composable
fun LockCanvasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else DarkColors,
        content = content,
    )
}
