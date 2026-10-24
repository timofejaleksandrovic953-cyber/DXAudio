package com.dxaudio

import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FileManagerScreen(
    onPlayAudio: (Uri, String) -> Unit,
    onPlayPlaylist: (Uri, String) -> Unit
) {
    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) } // Индикатор загрузки

    LaunchedEffect(currentDir) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val list = currentDir.listFiles()?.toList() ?: emptyList()
            val filtered = list.filter { file ->
                file.isDirectory || 
                file.name.endsWith(".mp3", true) || 
                file.name.endsWith(".flac", true) || 
                file.name.endsWith(".wav", true) || 
                file.name.endsWith(".ogg", true) || 
                file.name.endsWith(".m4a", true) ||
                file.name.endsWith(".m3u", true) ||
                file.name.endsWith(".m3u8", true) ||
                file.name.endsWith(".lrc", true)
            }
            files = filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = currentDir.absolutePath,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (currentDir.parentFile != null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { currentDir = currentDir.parentFile!! }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(".. (Наверх)", style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider()
                    }
                }

                items(files, key = { it.absolutePath }) { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (file.isDirectory) {
                                currentDir = file
                            } else {
                                val name = file.name.lowercase()
                                if (name.endsWith(".m3u") || name.endsWith(".m3u8")) {
                                    onPlayPlaylist(Uri.fromFile(file), file.name)
                                } else if (!name.endsWith(".lrc")) {
                                    onPlayAudio(Uri.fromFile(file), file.name)
                                }
                            }
                        }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when {
                            file.isDirectory -> Icons.Default.Folder
                            file.name.endsWith(".m3u", true) || file.name.endsWith(".m3u8", true) -> Icons.Default.PlaylistPlay
                            file.name.endsWith(".lrc", true) -> Icons.Default.Subject
                            else -> Icons.Default.MusicNote
                        }
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
