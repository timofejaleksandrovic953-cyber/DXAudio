package com.dxaudio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onOpenThemes: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        
        ListItem(
            headlineContent = { Text("Темы оформления") },
            supportingContent = { Text("Настройка цветов интерфейса") },
            modifier = Modifier.clickable { onOpenThemes() }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("О приложении") },
            supportingContent = { Text("DXAudio v1.0.0") }
        )
    }
}
