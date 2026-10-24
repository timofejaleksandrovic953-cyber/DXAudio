package com.dxaudio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dxaudio.theme.DxTheme
import com.dxaudio.theme.ThemeRepository

@Composable
fun ThemePickerScreen(
    current: DxTheme, 
    allThemes: List<DxTheme>,
    onSelect: (DxTheme) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { ThemeRepository(context) }
    var themes by remember { mutableStateOf(allThemes) }

    // Лаунчер для импорта .dxth файлов
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val newTheme = repo.importTheme(uri)
            if (newTheme != null) {
                // Обновляем список тем после успешного импорта
                themes = repo.getAllThemes() 
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Оформление", 
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = { picker.launch(arrayOf("*/*")) }, 
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Импортировать файл .dxth")
        }
        
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(themes, key = { it.id }) { theme ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(theme) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кружок с основным цветом темы
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(theme.primary)
                    )
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = theme.name, 
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "от ${theme.author}", 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Галочка текущей темы
                    if (theme.id == current.id) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Выбрано",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}
