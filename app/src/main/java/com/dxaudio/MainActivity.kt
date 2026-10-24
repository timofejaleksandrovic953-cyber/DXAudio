package com.dxaudio

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dxaudio.theme.DxThemeProvider
import com.dxaudio.theme.ThemeRepository
import com.dxaudio.ui.ThemePickerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

sealed class Screen {
    object Files : Screen()
    object Playlists : Screen()
    object Settings : Screen()
    object Themes : Screen()
    object Player : Screen()
}

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    
    // Состояния для обработки внешних интентов
    private var externalAudioUri by mutableStateOf<Uri?>(null)
    private var externalThemeUri by mutableStateOf<Uri?>(null)
    private var externalPlaylistUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val context = LocalContext.current
            val repo = remember { ThemeRepository(context) }
            var currentTheme by remember { mutableStateOf(repo.getDefaultTheme()) }
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Files) }
            
            var currentTrackTitle by remember { mutableStateOf("Ничего не играет") }
            var isPlaying by remember { mutableStateOf(false) }

            val exoPlayer = remember {
                ExoPlayer.Builder(context).build().also { 
                    player = it
                    it.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    })
                }
            }

            // Обработка внешних файлов (Аудио)
            LaunchedEffect(externalAudioUri) {
                externalAudioUri?.let { uri ->
                    exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                    exoPlayer.prepare()
                    exoPlayer.play()
                    currentTrackTitle = "Внешний файл"
                    currentScreen = Screen.Player // Принудительный переход на экран плеера
                    externalAudioUri = null
                }
            }

            // Обработка внешних плейлистов (M3U)
            LaunchedEffect(externalPlaylistUri) {
                externalPlaylistUri?.let { uri ->
                    withContext(Dispatchers.IO) {
                        val mediaItems = parseM3u(context, uri)
                        if (mediaItems.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                exoPlayer.setMediaItems(mediaItems)
                                exoPlayer.prepare()
                                exoPlayer.play()
                                currentTrackTitle = "Плейлист"
                                currentScreen = Screen.Player
                            }
                        }
                    }
                    externalPlaylistUri = null
                }
            }

            // Обработка внешних тем (.dxth)
            LaunchedEffect(externalThemeUri) {
                externalThemeUri?.let { uri ->
                    val theme = repo.importTheme(uri)
                    if (theme != null) {
                        currentTheme = theme
                        // Не перекидываем на плеер, оставляем в настройках
                    }
                    externalThemeUri = null
                }
            }

            DxThemeProvider(currentTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Scaffold(
                        bottomBar = {
                            if (currentScreen != Screen.Player) {
                                Column {
                                    // Мини-плеер
                                    if (currentTrackTitle != "Ничего не играет") {
                                        MiniPlayer(
                                            title = currentTrackTitle,
                                            isPlaying = isPlaying,
                                            onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                            onClick = { currentScreen = Screen.Player }
                                        )
                                    }
                                    // Нижняя навигация
                                    NavigationBar {
                                        NavigationBarItem(
                                            selected = currentScreen == Screen.Files,
                                            onClick = { currentScreen = Screen.Files },
                                            icon = { Icon(Icons.Default.List, contentDescription = null) },
                                            label = { Text("Файлы") }
                                        )
                                        NavigationBarItem(
                                            selected = currentScreen == Screen.Playlists,
                                            onClick = { currentScreen = Screen.Playlists },
                                            icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                                            label = { Text("Плейлисты") }
                                        )
                                        NavigationBarItem(
                                            selected = currentScreen == Screen.Settings || currentScreen == Screen.Themes,
                                            onClick = { currentScreen = Screen.Settings },
                                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                            label = { Text("Настройки") }
                                        )
                                    }
                                }
                            }
                        }
                    ) { paddingValues ->
                        Box(modifier = Modifier.padding(paddingValues)) {
                            when (currentScreen) {
                                Screen.Files -> FileManagerScreen(
                                    onPlayAudio = { uri, name -> externalAudioUri = uri },
                                    onPlayPlaylist = { uri, name -> externalPlaylistUri = uri }
                                )
                                Screen.Playlists -> {
                                    // Заглушка для плейлистов (пока просто кнопка открытия файла)
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Откройте .m3u файл в менеджере файлов, чтобы играть плейлисты")
                                    }
                                }
                                Screen.Settings -> SettingsScreen(
                                    onOpenThemes = { currentScreen = Screen.Themes }
                                )
                                Screen.Themes -> ThemePickerScreen(
                                    current = currentTheme,
                                    allThemes = repo.getAllThemes(), // Передаем встроенные темы
                                    onSelect = { theme ->
                                        currentTheme = theme
                                        currentScreen = Screen.Settings
                                    }
                                )
                                Screen.Player -> PlayerScreen(
                                    player = exoPlayer,
                                    title = currentTrackTitle,
                                    onBack = { currentScreen = Screen.Files }
                                )
                            }
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
            val uri = intent.data ?: return
            val mime = intent.type ?: ""
            val path = uri.path?.lowercase() ?: ""

            if (path.endsWith(".dxth") || mime == "application/zip" || mime == "application/octet-stream") {
                externalThemeUri = uri
            } else if (path.endsWith(".m3u") || path.endsWith(".m3u8") || mime.contains("mpegurl")) {
                externalPlaylistUri = uri
            } else {
                externalAudioUri = uri
            }
        }
    }

    // Парсер M3U плейлистов
    private fun parseM3u(context: android.content.Context, uri: Uri): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        // Если путь относительный, пытаемся сделать его абсолютным
                        val file = File(trimmed)
                        if (file.exists()) {
                            items.add(MediaItem.fromUri(Uri.fromFile(file)))
                        } else if (trimmed.startsWith("http")) {
                            items.add(MediaItem.fromUri(trimmed))
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return items
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}

@Composable
fun MiniPlayer(title: String, isPlaying: Boolean, onPlayPause: () -> Unit, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onPlayPause) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
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

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = player.duration
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            if (player.duration > 0) duration = player.duration
            delay(500)
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
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") }
            Text("Сейчас играет", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Красивая "обложка"
        Surface(
            modifier = Modifier.size(240.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

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
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(currentPosition), style = MaterialTheme.typography.bodySmall)
            Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { player.seekToPrevious() }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(36.dp))
            }
            
            Spacer(modifier = Modifier.width(24.dp))

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

            Spacer(modifier = Modifier.width(24.dp))

            IconButton(onClick = { player.seekToNext() }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Следующий", modifier = Modifier.size(36.dp))
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
