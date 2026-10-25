package com.dxaudio

import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun PlaylistsScreen(
    onPlayAudio: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    var playlists by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedPlaylist by remember { mutableStateOf<File?>(null) }
    var playlistTracks by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val playlistsDir = remember { File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "playlists").apply { mkdirs() } }

    fun refreshPlaylists() {
        playlists = playlistsDir.listFiles { file -> file.name.endsWith(".m3u", true) }?.toList() ?: emptyList()
    }

    LaunchedEffect(Unit) { refreshPlaylists() }

    LaunchedEffect(selectedPlaylist) {
        selectedPlaylist?.let { file ->
            playlistTracks = file.readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { Uri.parse(it.trim()) }
        } ?: run { playlistTracks = emptyList() }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Новый плейлист") },
            text = { OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it }, label = { Text("Название") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        File(playlistsDir, "${newPlaylistName}.m3u").writeText("#EXTM3U\n")
                        refreshPlaylists()
                    }
                    showCreateDialog = false
                    newPlaylistName = ""
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Отмена") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedPlaylist == null) {
            // Список плейлистов
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Плейлисты", style = MaterialTheme.typography.titleLarge)
                Button(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Создать")
                }
            }
            if (playlists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Нет плейлистов") }
            } else {
                LazyColumn {
                    items(playlists) { file ->
                        Row(Modifier.fillMaxWidth().clickable { selectedPlaylist = file }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(file.nameWithoutExtension, style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider()
                    }
                }
            }
        } else {
            // Треки внутри плейлиста
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedPlaylist = null }) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") }
                Text(selectedPlaylist!!.nameWithoutExtension, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (playlistTracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Плейлист пуст") }
            } else {
                LazyColumn {
                    items(playlistTracks) { uri ->
                        Row(Modifier.fillMaxWidth().clickable { onPlayAudio(uri, uri.lastPathSegment ?: "Трек") }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(12.dp))
                            Text(uri.lastPathSegment ?: "Неизвестный трек", style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
