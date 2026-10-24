package com.dxaudio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Subject
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
    onVisualizerStyleChange: (String) -> Unit
) {
    val context = LocalContext.current
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cacheClearedText by remember { mutableStateOf("Очистить кэш") }
    var showVisualizerDialog by remember { mutableStateOf(false) }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Очистка кэша") },
            text = { Text("Удалить временные файлы и кэш? Ваши темы и музыка останутся на месте.") },
            confirmButton = {
                TextButton(onClick = {
                    context.cacheDir.deleteRecursively()
                    cacheClearedText = "Кэш очищен!"
                    showClearCacheDialog = false
                }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { showClearCacheDialog = false }) { Text("Отмена") } }
        )
    }

    if (showVisualizerDialog) {
        AlertDialog(
            onDismissRequest = { showVisualizerDialog = false },
            title = { Text("Визуализатор") },
            text = {
                Column {
                    listOf("Отключен", "Волна", "Спектр (Cava)").forEach { style ->
                        Row(
                            Modifier.fillMaxWidth().clickable { 
                                onVisualizerStyleChange(style)
                                showVisualizerDialog = false 
                            }.padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = visualizerStyle == style, onClick = { 
                                onVisualizerStyleChange(style)
                                showVisualizerDialog = false 
                            })
                            Text(style, modifier = Modifier.padding(start = 8.dp))
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
            headlineContent = { Text("Визуализатор") },
            supportingContent = { Text(visualizerStyle) },
            leadingContent = { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { showVisualizerDialog = true }
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
            supportingContent = { Text("DXAudio v1.2.0\nСделано на GitHub Actions") },
            leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        )
    }
}
