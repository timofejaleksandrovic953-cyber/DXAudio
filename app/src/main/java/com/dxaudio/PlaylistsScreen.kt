package com.dxaudio

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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

    val playlistsDir = remember { 
        File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "playlists").apply { mkdirs() } 
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri != null && selectedPlaylist != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    selectedPlaylist!!.inputStream().use { input -> input.copyTo(out) }
                }
                Toast.makeText(context, "Плейлист сохранен", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                // Сетка карточек для плейлистов
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(playlists) { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { selectedPlaylist = file },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PlaylistPlay, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = file.nameWithoutExtension, 
                                        style = MaterialTheme.typography.titleMedium, 
                                        maxLines = 2, 
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedPlaylist = null }) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") }
                Text(
                    text = selectedPlaylist!!.nameWithoutExtension, 
                    style = MaterialTheme.typography.titleLarge, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { exportLauncher.launch("${selectedPlaylist!!.nameWithoutExtension}.m3u") }) {
                    Icon(Icons.Default.Share, contentDescription = "Экспорт", tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            if (playlistTracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Плейлист пуст") }
            } else {
                LazyColumn {
                    items(playlistTracks) { uri ->
                        Row(
                            Modifier.fillMaxWidth().clickable { 
                                val trackName = uri.lastPathSegment?.substringAfterLast("/") ?: "Трек"
                                onPlayAudio(uri, trackName) 
                            }.padding(16.dp), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(12.dp))
                            Text(uri.lastPathSegment?.substringAfterLast("/") ?: "Неизвестный трек", style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
