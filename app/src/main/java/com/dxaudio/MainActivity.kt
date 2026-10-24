package com.dxaudio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dxaudio.theme.DxTheme
import com.dxaudio.theme.DxThemeProvider
import com.dxaudio.theme.ThemeRepository
import com.dxaudio.ui.ThemePickerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class LyricLine(val timeMs: Long, val text: String)

sealed class Screen {
    object Files : Screen()
    object Playlists : Screen()
    object Settings : Screen()
    object Themes : Screen()
    object Player : Screen()
}

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    
    private var externalAudioUri by mutableStateOf<Uri?>(null)
    private var externalThemeUri by mutableStateOf<Uri?>(null)
    private var externalPlaylistUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val context = LocalContext.current
            val repo = remember { ThemeRepository(context) }
            
            // Загружаем сохраненную тему
            var currentTheme by remember { mutableStateOf(repo.getSavedTheme()) }
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Files) }
            
            var currentTrackTitle by remember { mutableStateOf("Ничего не играет") }
            var currentLyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
            var isPlaying by remember { mutableStateOf(false) }

            val exoPlayer = remember {
                ExoPlayer.Builder(context).build().also { 
                    player = it
                    it.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    })
                }
            }

            LaunchedEffect(externalAudioUri) {
                externalAudioUri?.let { uri ->
                    exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                    exoPlayer.prepare()
                    exoPlayer.play()
                    currentTrackTitle = "Внешний файл"
                    withContext(Dispatchers.IO) { currentLyrics = loadLyrics(uri) }
                    currentScreen = Screen.Player
                    externalAudioUri = null
                }
            }

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
                                currentLyrics = emptyList()
                                currentScreen = Screen.Player
                            }
                        }
                    }
                    externalPlaylistUri = null
                }
            }

            LaunchedEffect(externalThemeUri) {
                externalThemeUri?.let { uri ->
                    val theme = repo.importTheme(uri)
                    if (theme != null) {
                        currentTheme = theme
                        repo.saveThemeId(theme.id) // Сохраняем
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
                                    if (currentTrackTitle != "Ничего не играет") {
                                        MiniPlayer(currentTheme, currentTrackTitle, isPlaying, { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }, { currentScreen = Screen.Player })
                                    }
                                    NavigationBar {
                                        NavigationBarItem(selected = currentScreen == Screen.Files, onClick = { currentScreen = Screen.Files }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Файлы") })
                                        NavigationBarItem(selected = currentScreen == Screen.Playlists, onClick = { currentScreen = Screen.Playlists }, icon = { Icon(Icons.Default.PlaylistPlay, null) }, label = { Text("Плейлисты") })
                                        NavigationBarItem(selected = currentScreen == Screen.Settings || currentScreen == Screen.Themes, onClick = { currentScreen = Screen.Settings }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Настройки") })
                                    }
                                }
                            }
                        }
                    ) { paddingValues ->
                        Box(modifier = Modifier.padding(paddingValues)) {
                            when (currentScreen) {
                                Screen.Files -> FileManagerScreen(
                                    onPlayAudio = { uri, _ -> externalAudioUri = uri },
                                    onPlayPlaylist = { uri, _ -> externalPlaylistUri = uri }
                                )
                                Screen.Playlists -> PlaylistsScreen(onPlayPlaylist = { uri, _ -> externalPlaylistUri = uri })
                                Screen.Settings -> SettingsScreen(onOpenThemes = { currentScreen = Screen.Themes })
                                Screen.Themes -> ThemePickerScreen(
                                    current = currentTheme,
                                    allThemes = repo.getAllThemes(),
                                    onSelect = { theme -> 
                                        currentTheme = theme
                                        repo.saveThemeId(theme.id) // Сохраняем при выборе
                                        currentScreen = Screen.Settings 
                                    }
                                )
                                Screen.Player -> PlayerScreen(
                                    theme = currentTheme,
                                    player = exoPlayer,
                                    title = currentTrackTitle,
                                    lyrics = currentLyrics,
                                    onBack = { currentScreen = Screen.Files }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadLyrics(audioUri: Uri): List<LyricLine> {
        return try {
            val path = audioUri.path ?: return emptyList()
            val lrcFile = File(path.substringBeforeLast(".") + ".lrc")
            if (!lrcFile.exists()) return emptyList()

            val lyrics = mutableListOf<LyricLine>()
            val regex = Regex("\\[(\\d{2}):(\\d{2}\\.\\d{2})](.*)")
            lrcFile.forEachLine { line ->
                val match = regex.find(line)
                if (match != null) {
                    val min = match.groupValues[1].toLong()
                    val sec = match.groupValues[2].toDouble()
                    val text = match.groupValues[3].trim()
                    if (text.isNotEmpty()) {
                        val timeMs = (min * 60 * 1000) + (sec * 1000).toLong()
                        lyrics.add(LyricLine(timeMs, text))
                    }
                }
            }
            lyrics.sortedBy { it.timeMs }
        } catch (e: Exception) { emptyList() }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            val mime = intent.type ?: ""
            val path = uri.path?.lowercase() ?: ""
            if (path.endsWith(".dxth") || mime == "application/zip" || mime == "application/octet-stream") externalThemeUri = uri
            else if (path.endsWith(".m3u") || path.endsWith(".m3u8") || mime.contains("mpegurl")) externalPlaylistUri = uri
            else externalAudioUri = uri
        }
    }

    private fun parseM3u(context: android.content.Context, uri: Uri): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val file = File(trimmed)
                        if (file.exists()) items.add(MediaItem.fromUri(Uri.fromFile(file)))
                        else if (trimmed.startsWith("http")) items.add(MediaItem.fromUri(trimmed))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return items
    }

    override fun onDestroy() { super.onDestroy(); player?.release(); player = null }
}

@Composable
fun MiniPlayer(theme: DxTheme, title: String, isPlaying: Boolean, onPlayPause: () -> Unit, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(8.dp).shadow(4.dp, RoundedCornerShape(12.dp)).clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = theme.primary)
            Spacer(Modifier.width(12.dp))
            Text(text = title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onPlayPause) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = theme.primary)
            }
        }
    }
}

@Composable
fun PlayerScreen(
    theme: DxTheme,
    player: ExoPlayer,
    title: String,
    lyrics: List<LyricLine>,
    onBack: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableStateOf(player.currentPosition) }
    var duration by remember { mutableStateOf(player.duration) }
    var showLyrics by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) { duration = player.duration }
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

    LaunchedEffect(currentPosition, showLyrics) {
        if (showLyrics && lyrics.isNotEmpty()) {
            val currentLineIndex = lyrics.indexOfLast { it.timeMs <= currentPosition }
            if (currentLineIndex >= 0) listState.animateScrollToItem(currentLineIndex)
        }
    }

    // Красивый градиентный фон
    val gradient = Brush.verticalGradient(
        colors = listOf(theme.primary.copy(alpha = 0.15f), theme.background),
        startY = 0f,
        endY = 1200f
    )

    Column(modifier = Modifier.fillMaxSize().background(gradient).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") }
            Text(if (showLyrics) "Текст песни" else "Сейчас играет", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (lyrics.isNotEmpty()) {
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(if (showLyrics) Icons.Default.MusicNote else Icons.Default.Subject, contentDescription = "Текст")
                }
            }
        }

        if (showLyrics && lyrics.isNotEmpty()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 100.dp)) {
                items(lyrics) { line ->
                    val isCurrent = line.timeMs <= currentPosition && line.timeMs + 5000 > currentPosition
                    Text(
                        text = line.text,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        color = if (isCurrent) theme.primary else theme.onSurface,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (isCurrent) 20.sp else 16.sp
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
            // Красивая "обложка" с тенью
            Surface(
                modifier = Modifier.size(260.dp).shadow(12.dp, MaterialTheme.shapes.extraLarge),
                shape = MaterialTheme.shapes.extraLarge, 
                color = theme.surface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(120.dp), tint = theme.primary)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.weight(1f))
        }

        // Стилизованный слайдер
        Slider(
            value = currentPosition.toFloat().coerceIn(0f, if (duration > 0) duration.toFloat() else 1f),
            onValueChange = { player.seekTo(it.toLong()) },
            valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
            colors = SliderDefaults.colors(
                thumbColor = theme.primary,
                activeTrackColor = theme.primary,
                inactiveTrackColor = theme.surface
            )
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(currentPosition), style = MaterialTheme.typography.bodySmall, color = theme.onSurface)
            Text(formatTime(duration), style = MaterialTheme.typography.bodySmall, color = theme.onSurface)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопки управления
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { player.seekToPrevious() }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(40.dp), tint = theme.onSurface)
            }
            Spacer(modifier = Modifier.width(24.dp))
            // Главная кнопка Play
            Surface(
                modifier = Modifier.size(84.dp).clickable { 
                    if (isPlaying) player.pause() 
                    else {
                        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                        player.play() 
                    }
                }.shadow(8.dp, CircleShape),
                shape = CircleShape,
                color = theme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(48.dp), tint = theme.onPrimary)
                }
            }
            Spacer(modifier = Modifier.width(24.dp))
            IconButton(onClick = { player.seekToNext() }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Следующий", modifier = Modifier.size(40.dp), tint = theme.onSurface)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
