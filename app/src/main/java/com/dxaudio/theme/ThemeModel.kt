package com.dxaudio.theme

import androidx.compose.ui.graphics.Color

data class DxTheme(
    val id: String,
    val name: String,
    val author: String = "Unknown",
    val version: String = "1.0.0",
    val primary: Color = Color(0xFFBB86FC),
    val onPrimary: Color = Color.Black,
    val secondary: Color = Color(0xFF03DAC6),
    val background: Color = Color(0xFF121212),
    val surface: Color = Color(0xFF1E1E1E),
    val onBackground: Color = Color.White,
    val onSurface: Color = Color(0xFFE0E0E0),
    val accent: Color = Color(0xFFCF6679),
    val playerBackground: Color = Color(0xFF0A0A0A),
    val progressBar: Color = Color(0xFFBB86FC),
    val progressBg: Color = Color(0xFF333333),
    val titleSizeSp: Float = 20f,
    val bodySizeSp: Float = 14f,
    val cornerRadiusDp: Float = 16f
)
