package com.dxaudio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenThemes: () -> Unit,
    autoShowLyrics: Boolean,
    onAutoShowLyricsChange: (Boolean) -> Unit,
    visualizerStyle: String,
    onVisualizerStyleChange: (String) -> Unit,
    visualizerColor: String,
    onVisualizerColorChange: (String) -> Unit
) {
    val context = LocalContext.current
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cacheClearedText by remember { mutableStateOf("Очистить кэш") }
    var showVisualizerDialog by remember { mutableStateOf(false) }
    var showVizColorDialog by remember { mutableStateOf(false) }
    
    // Пасхалка
    var clickCount by remember { mutableIntStateOf(0) }
    var showEasterEgg by remember { mutableStateOf(false) }

    // Получаем реальную версию приложения из Gradle
    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    if (showEasterEgg) {
        AlertDialog(
            onDismissRequest = { showEasterEgg = false },
            title = { Text("🎉 Пасхалка разблокирована!") },
            text = { Text("Ты нашел секретное сообщение!\n\nЭтот плеер был собран с любовью, нейросетями и большим количеством кофе. Vibe-coding forever! 🚀\n\nА еще у нас есть 6 визуализаторов. Выбери свой в настройках!") },
            confirmButton = { TextButton(onClick = { showEasterEgg = false }) { Text("Круто!") } }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Очистка кэша") },
            text = { Text("Удалить временные файлы и кэш?") },
            confirmButton = {
                TextButton(onClick = { context.cacheDir.deleteRecursively(); cacheClearedText = "Кэш очищен!"; showClearCacheDialog = false }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { showClearCacheDialog = false }) { Text("Отмена") } }
        )
    }

    if (showVisualizerDialog) {
        AlertDialog(
            onDismissRequest = { showVisualizerDialog = false },
            title = { Text("Стиль визуализатора") },
            text = {
                Column {
                    listOf("Отключен", "Волна", "Спектр (Cava)", "Круг", "Зеркальные бары", "Заполненная волна", "Точки").forEach { style ->
                        Row(Modifier.fillMaxWidth().clickable { onVisualizerStyleChange(style); showVisualizerDialog = false }.padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = visualizerStyle == style, onClick = { onVisualizerStyleChange(style); showVisualizerDialog = false })
                            Text(style, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showVizColorDialog) {
        AlertDialog(
            onDismissRequest = { showVizColorDialog = false },
            title = { Text("Цвет визуализатора") },
            text = {
                Column {
                    listOf("Цвет темы", "Радуга", "Белый").forEach { color ->
                        Row(Modifier.fillMaxWidth().clickable { onVisualizerColorChange(color); showVizColorDialog = false }.padding(vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = visualizerColor == color, onClick = { onVisualizerColorChange(color); showVizColorDialog = false })
                            Text(color, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        
        Text("Внешний вид", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ListItem(
            headlineContent = { Text("Темы оформления") },
            supportingContent = { Text("Настройка цветов интерфейса") },
            leadingContent = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { onOpenThemes() }
        )
        HorizontalDivider()

        Text("Воспроизведение", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ListItem(
            headlineContent = { Text("Автопоказ текста") },
            supportingContent = { Text("Сразу открывать .lrc при запуске трека") },
            leadingContent = { Icon(Icons.Default.Subject, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Switch(checked = autoShowLyrics, onCheckedChange = onAutoShowLyricsChange) }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Стиль визуализатора") },
            supportingContent = { Text(visualizerStyle) },
            leadingContent = { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { showVisualizerDialog = true }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Цвет визуализатора") },
            supportingContent = { Text(visualizerColor) },
            leadingContent = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { showVizColorDialog = true }
        )
        HorizontalDivider()

        Text("Система", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ListItem(
            headlineContent = { Text(cacheClearedText) },
            supportingContent = { Text("Освободить место на устройстве") },
            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { if (cacheClearedText == "Очистить кэш") showClearCacheDialog = true }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("О приложении") },
            // Здесь теперь подставляется реальная версия (например, 1.0.42 или 1.0.1)
            supportingContent = { Text("DXAudio v$appVersion\nНажми 4 раза для сюрприза") },
            leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable {
                clickCount++
                if (clickCount >= 4) {
                    showEasterEgg = true
                    clickCount = 0 // Сбрасываем счетчик
                }
            }
        )
    }
}
