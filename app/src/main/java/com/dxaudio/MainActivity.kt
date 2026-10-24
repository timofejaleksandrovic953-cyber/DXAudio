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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dxaudio.theme.DxThemeProvider
import com.dxaudio.theme.ThemeRepository
import com.dxaudio.ui.ThemePickerScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var externalUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val context = LocalContext.current
            val repo = remember { ThemeRepository(context) }
            var currentTheme by remember { mutableStateOf(repo.defaultTheme()) }
            
            var currentScreen by remember { mutableStateOf("library") }
            var showThemes by remember { mutableStateOf(false) }
            
            var currentTrackTitle by remember { mutableStateOf("") }

            val exoPlayer = remember {
                ExoPlayer.Builder(context).build().also { player = it }
            }

            // Обработка открытия файла через "Открыть с помощью..."
            LaunchedEffect(externalUri) {
                externalUri?.let { uri ->
                    val mediaItem = MediaItem.fromUri(uri)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                    currentTrackTitle = "Внешний файл"
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri -> externalUri = uri }
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
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableStateOf(player.currentPosition) }
    var duration by remember { mutableStateOf(player.duration) }

    // Слушатель изменений состояния плеера
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = player.duration
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Безопасный таймер обновления ползунка
    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            if (player.duration > 0) {
                duration = player.duration
            }
            delay(500) // Обновляем дважды в секунду
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
            }
            Text("Сейчас играет", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Slider(
            value = currentPosition.toFloat().coerceIn(0f, if (duration > 0) duration.toFloat() else 1f),
            onValueChange = { player.seekTo(it.toLong()) },
            valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(currentPosition), style = MaterialTheme.typography.bodySmall)
            Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { 
                    if (isPlaying) player.pause() 
                    else {
                        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                        player.play() 
                    }
                },
                modifier = Modifier.size(80.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(64.dp))
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
