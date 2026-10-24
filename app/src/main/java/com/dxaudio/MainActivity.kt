package com.dxaudio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.material3.Player
import com.dxaudio.theme.DxTheme
import com.dxaudio.theme.DxThemeProvider
import com.dxaudio.theme.ThemeRepository
import com.dxaudio.ui.ThemePickerScreen

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    // Храним URI, если приложение открыли через "Открыть с помощью"
    private var externalUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val context = LocalContext.current
            val repo = remember { ThemeRepository(context) }
            var currentTheme by remember { mutableStateOf(repo.defaultTheme()) }
            
            // Состояния экранов
            var currentScreen by remember { mutableStateOf("library") }
            var showThemes by remember { mutableStateOf(false) }
            
            // Текущий играющий трек
            var currentTrackTitle by remember { mutableStateOf("") }
            var currentTrackUri by remember { mutableStateOf<Uri?>(null) }

            // Инициализация плеера
            val exoPlayer = remember {
                ExoPlayer.Builder(context).build().also { player = it }
            }

            // Если пришел интент извне — сразу включаем плеер
            LaunchedEffect(externalUri) {
                externalUri?.let { uri ->
                    val mediaItem = MediaItem.fromUri(uri)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                    currentTrackTitle = "Внешний файл"
                    currentTrackUri = uri
                    currentScreen = "player"
                }
            }

            DxThemeProvider(currentTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        showThemes -> {
                            ThemePickerScreen(
                                current = currentTheme,
                                onSelect = { theme ->
                                    currentTheme = theme
                                    showThemes = false
                                }
                            )
                        }
                        currentScreen == "library" -> {
                            LibraryScreen(
                                onTrackClick = { track ->
                                    val mediaItem = MediaItem.fromUri(track.uri)
                                    exoPlayer.setMediaItem(mediaItem)
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                    currentTrackTitle = track.title
                                    currentTrackUri = track.uri
                                    currentScreen = "player"
                                },
                                onOpenThemes = { showThemes = true }
                            )
                        }
                        currentScreen == "player" -> {
                            PlayerScreen(
                                player = exoPlayer,
                                title = currentTrackTitle,
                                onBack = { currentScreen = "library" }
                            )
                        }
                    }
                }
            }
        }
    }

    // Обработка "Открыть с помощью"
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                externalUri = uri
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}

@Composable
fun LibraryScreen(
    onTrackClick: (AudioTrack) -> Unit,
    onOpenThemes: () -> Unit
) {
    val context = LocalContext.current
    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

    // Запрос разрешений
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
            tracks = AudioRepository.getAudioFiles(context)
        } else {
            permissionLauncher.launch(arrayOf(permission))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Шапка
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Моя музыка", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onOpenThemes) { Text("Темы") }
        }

        if (!hasPermission) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = {
                    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    permissionLauncher.launch(arrayOf(permission))
                }) {
                    Text("Дать доступ к музыке")
                }
            }
        } else if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Музыка не найдена")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tracks) { track ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(track) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(
    player: ExoPlayer,
    title: String,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Стандартный UI-плеер от Media3
        Player(
            player = player,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
