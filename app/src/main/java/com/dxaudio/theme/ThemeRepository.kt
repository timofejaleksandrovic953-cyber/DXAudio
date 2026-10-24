package com.dxaudio.theme

import android.content.Context
import java.io.File

class ThemeRepository(private val context: Context) {
    private val themesDir: File
        get() = File(context.filesDir, "themes").apply { if (!exists()) mkdirs() }

    fun listThemes(): List<DxTheme> =
        themesDir.listFiles { f -> f.extension == "dxth" }
            ?.mapNotNull { ThemeLoader.load(it) }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun importTheme(uri: android.net.Uri): DxTheme? {
        val tempFile = File(context.cacheDir, "import.dxth")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        val theme = ThemeLoader.load(tempFile) ?: return null
        val target = File(themesDir, "${theme.id}.dxth")
        tempFile.copyTo(target, overwrite = true)
        tempFile.delete()
        return theme
    }

    fun defaultTheme(): DxTheme = DxTheme(id = "default", name = "Default Dark")
}
