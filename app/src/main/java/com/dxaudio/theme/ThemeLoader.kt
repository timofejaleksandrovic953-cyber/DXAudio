package com.dxaudio.theme

import android.graphics.Color.parseColor
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

object ThemeLoader {
    fun load(file: File): DxTheme? = try {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("theme.json") ?: return null
            val jsonText = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            parseJson(jsonText)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    private fun parseJson(text: String): DxTheme {
        val root = JSONObject(text)
        val colors = root.optJSONObject("colors") ?: JSONObject()
        val fonts = root.optJSONObject("fonts") ?: JSONObject()
        val shape = root.optJSONObject("shape") ?: JSONObject()

        return DxTheme(
            id = root.optString("id", "unnamed"),
            name = root.optString("name", "Unnamed Theme"),
            author = root.optString("author", "Unknown"),
            version = root.optString("version", "1.0.0"),
            primary = colors.color("primary", 0xFFBB86FC),
            onPrimary = colors.color("on_primary", 0xFF000000),
            secondary = colors.color("secondary", 0xFF03DAC6),
            background = colors.color("background", 0xFF121212),
            surface = colors.color("surface", 0xFF1E1E1E),
            onBackground = colors.color("on_background", 0xFFFFFFFF),
            onSurface = colors.color("on_surface", 0xFFE0E0E0),
            accent = colors.color("accent", 0xFFCF6679),
            playerBackground = colors.color("player_background", 0xFF0A0A0A),
            progressBar = colors.color("progress_bar", 0xFFBB86FC),
            progressBg = colors.color("progress_bg", 0xFF333333),
            titleSizeSp = fonts.optDouble("title_size_sp", 20.0).toFloat(),
            bodySizeSp = fonts.optDouble("body_size_sp", 14.0).toFloat(),
            cornerRadiusDp = shape.optDouble("corner_radius_dp", 16.0).toFloat()
        )
    }

    private fun JSONObject.color(key: String, fallback: Long): Color {
        val raw = optString(key, "")
        if (raw.isBlank()) return Color(fallback)
        return try { Color(parseColor(raw)) } catch (e: Exception) { Color(fallback) }
    }
}
