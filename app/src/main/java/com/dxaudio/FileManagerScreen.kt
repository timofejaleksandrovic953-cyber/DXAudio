package com.dxaudio

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    onPlayAudio: (Uri, String) -> Unit,
    onPlayPlaylist: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dx_prefs", android.content.Context.MODE_PRIVATE) }
    
    var currentDirUri by remember { mutableStateOf(prefs.getString("saf_tree_uri", null)?.let { Uri.parse(it) }) }
    var files by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Поиск и сортировка
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Имя") } // Имя, Дата

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString("saf_tree_uri", it.toString()).apply()
            currentDirUri = it
        }
    }

    LaunchedEffect(currentDirUri) {
        if (currentDirUri != null) {
            isLoading = true
            withContext(Dispatchers.IO) {
                val root = DocumentFile.fromTreeUri(context, currentDirUri!!)
                val list = root?.listFiles()?.toList() ?: emptyList()
                val filtered = list.filter { file ->
                    file.isDirectory || 
                    file.name?.endsWith(".mp3", true) == true || 
                    file.name?.endsWith(".flac", true) == true || 
                    file.name?.endsWith(".wav", true) == true || 
                    file.name?.endsWith(".ogg", true) == true || 
                    file.name?.endsWith(".m4a", true) == true ||
                    file.name?.endsWith(".m3u", true) == true ||
                    file.name?.endsWith(".lrc", true) == true
                }
                files = filtered
            }
            isLoading = false
        }
    }

    // Фильтрация и сортировка
    val processedFiles = remember(files, searchQuery, sortBy) {
        val filtered = files.filter { it.name?.contains(searchQuery, ignoreCase = true) == true || searchQuery.isBlank() }
        if (sortBy == "Имя") {
            filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() }))
        } else {
            filtered.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified() }).reversed())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (currentDirUri == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { folderPickerLauncher.launch(null) }) {
                    Text("Выбрать папку с музыкой")
                }
            }
        } else {
            // Панель управления (Поиск и сортировка)
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sortBy == "Имя", onClick = { sortBy = "Имя" }, label = { Text("По имени") })
                    FilterChip(selected = sortBy == "Дата", onClick = { sortBy = "Дата" }, label = { Text("По дате") })
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { folderPickerLauncher.launch(null) }) { Text("Сменить папку") }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(processedFiles, key = { it.uri.toString() }) { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (file.isDirectory) {
                                    currentDirUri = file.uri 
                                } else {
                                    val name = file.name ?: "Unknown"
                                    if (name.endsWith(".m3u", true)) {
                                        onPlayPlaylist(file.uri, name)
                                    } else if (!name.endsWith(".lrc", true)) {
                                        onPlayAudio(file.uri, name)
                                    }
                                }
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when {
                                file.isDirectory -> Icons.Default.Folder
                                file.name?.endsWith(".m3u", true) == true -> Icons.Default.PlaylistPlay
                                file.name?.endsWith(".lrc", true) == true -> Icons.Default.Subject
                                else -> Icons.Default.MusicNote
                            }
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = file.name ?: "Без имени", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
