package com.dxaudio.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.io.File

class ThemeRepository(private val context: Context) {
    private val themesDir: File
        get() = File(context.filesDir, "themes").apply { if (!exists()) mkdirs() }
    
    private val prefs = context.getSharedPreferences("dx_prefs", Context.MODE_PRIVATE)

    fun getBuiltInThemes(): List<DxTheme> = listOf(
        DxTheme(
            id = "gold_default", name = "Golden Luxury", author = "DXAudio",
            primary = Color(0xFFFFD700), onPrimary = Color.Black, background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E), onBackground = Color.White, onSurface = Color(0xFFE0E0E0),
            accent = Color(0xFFFFC107), progressBar = Color(0xFFFFD700)
        ),
        DxTheme(
            id = "lime_neon", name = "Neon Lime", author = "DXAudio",
            primary = Color(0xFF32CD32), onPrimary = Color.Black, background = Color(0xFF0F1210),
            surface = Color(0xFF1A1F1A), onBackground = Color.White, onSurface = Color(0xFFE0E0E0),
            accent = Color(0xFF7CFC00), progressBar = Color(0xFF32CD32)
        ),
        DxTheme(
            id = "orange_burst", name = "Orange Burst", author = "DXAudio",
            primary = Color(0xFFFF9800), onPrimary = Color.Black, background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E), onBackground = Color.White, onSurface = Color(0xFFE0E0E0),
            accent = Color(0xFFFF5722), progressBar = Color(0xFFFF9800)
        ),
        DxTheme(
            id = "dark_amethyst", name = "Dark Amethyst", author = "DXAudio",
            primary = Color(0xFFBB86FC), onPrimary = Color.Black, background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E), onBackground = Color.White, onSurface = Color(0xFFE0E0E0),
            accent = Color(0xFF03DAC6), progressBar = Color(0xFFBB86FC)
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

    // Сохранение и загрузка выбранной темы
    fun saveThemeId(id: String) {
        prefs.edit().putString("theme_id", id).apply()
    }

    fun getSavedTheme(): DxTheme {
        val savedId = prefs.getString("theme_id", "gold_default") ?: "gold_default"
        return getAllThemes().find { it.id == savedId } ?: getBuiltInThemes().first()
    }
    
    fun getDefaultTheme() = getBuiltInThemes().first()
}
