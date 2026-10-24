package com.dxaudio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dxaudio.theme.DxTheme
import com.dxaudio.theme.ThemeRepository

@Composable
fun ThemePickerScreen(
    current: DxTheme, 
    allThemes: List<DxTheme>, // <-- Добавлен параметр
    onSelect: (DxTheme) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { ThemeRepository(context) }
    var themes by remember { mutableStateOf(allThemes) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val newTheme = repo.importTheme(uri)
            if (newTheme != null) {
                themes = repo.getAllThemes() // Обновляем список после импорта
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Выбор темы", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Импортировать .dxth")
        }
        Spacer(Modifier.height(12.dp))
        
        LazyColumn {
            items(themes, key = { it.id }) { theme ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(theme) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = MaterialTheme.shapes.small, color = theme.primary, modifier = Modifier.size(32.dp)) {}
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(theme.name, style = MaterialTheme.typography.bodyMedium)
                        Text("by ${theme.author}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (theme.id == current.id) Text("✓")
                }
                HorizontalDivider()
            }
        }
    }
}
