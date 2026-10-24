package com.dxaudio.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

@Composable
fun DxThemeProvider(theme: DxTheme, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = theme.primary,
        onPrimary = theme.onPrimary,
        secondary = theme.secondary,
        background = theme.background,
        onBackground = theme.onBackground,
        surface = theme.surface,
        onSurface = theme.onSurface,
        surfaceVariant = theme.surface, // Добавили для нижней панели и мини-плеера
        onSurfaceVariant = theme.onSurface,
        error = theme.accent
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography.copy(
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontSize = theme.titleSizeSp.sp),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontSize = theme.bodySizeSp.sp)
        ),
        content = content
    )
}
