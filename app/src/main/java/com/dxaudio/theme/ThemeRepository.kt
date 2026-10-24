package com.dxaudio.theme

import android.content.Context
import android.graphics.Color.parseColor
import androidx.compose.ui.graphics.Color
import java.io.File

class ThemeRepository(private val context: Context) {
    private val themesDir: File
        get() = File(context.filesDir, "themes").apply { if (!exists()) mkdirs() }

    // Встроенные темы
    fun getBuiltInThemes(): List<DxTheme> = listOf(
        DxTheme(
            id = "default_dark", name = "Default Dark", author = "DXAudio",
            primary = Color(0xFFBB86FC), onPrimary = Color.Black, background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E), onBackground = Color.White, onSurface = Color(0xFFE0E0E0)
        ),
        DxTheme(
            id = "orange_burst", name = "Orange Burst", author = "DXAudio",
            primary = Color(0xFFFF9800), onPrimary = Color.Black, background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E), onBackground = Color.White, onSurface = Color(0xFFE0E0E0),
            accent = Color(0xFFFF5722), progressBar = Color(0xFFFF9800)
        )
    )

    fun getAllThemes(): List<DxTheme> {
        val custom = themesDir.listFiles { f -> f.extension == "dxth" }
            ?.mapNotNull { ThemeLoader.load(it) } ?: emptyList()
        return getBuiltInThemes() + custom
    }

    fun importTheme(uri: android.net.Uri): DxTheme? {
        return try {
            val tempFile = File(context.cacheDir, "import.dxth")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            val theme = ThemeLoader.load(tempFile) ?: return null
            val target = File(themesDir, "${theme.id}.dxth")
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
            theme
        } catch (e: Exception) { null }
    }

    fun getDefaultTheme() = getBuiltInThemes().first()
}
