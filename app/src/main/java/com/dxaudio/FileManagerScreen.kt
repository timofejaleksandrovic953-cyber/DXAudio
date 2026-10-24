package com.dxaudio

import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
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

    LaunchedEffect(currentDir) {
        withContext(Dispatchers.IO) {
            val list = currentDir.listFiles()?.toList() ?: emptyList()
            files = list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Хлебные крошки / Текущая папка
        Text(
            text = currentDir.absolutePath,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

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
                }
            }

            items(files) { file ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (file.isDirectory) {
                            currentDir = file
                        } else {
                            val name = file.name.lowercase()
                            if (name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".m4a")) {
                                onPlayAudio(Uri.fromFile(file), file.name)
                            } else if (name.endsWith(".m3u") || name.endsWith(".m3u8")) {
                                onPlayPlaylist(Uri.fromFile(file), file.name)
                            }
                        }
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (file.isDirectory) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
