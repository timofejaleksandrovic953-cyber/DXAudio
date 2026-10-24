package com.dxaudio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import kotlin.math.abs
import kotlin.math.sqrt

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
            val prefs = remember { context.getSharedPreferences("dx_prefs", android.content.Context.MODE_PRIVATE) }
            
            var currentTheme by remember { mutableStateOf(repo.getSavedTheme()) }
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Files) }
            
            var autoShowLyrics by remember { mutableStateOf(prefs.getBoolean("auto_lyrics", false)) }
            var visualizerStyle by remember { mutableStateOf(prefs.getString("viz_style", "Волна") ?: "Волна") }
            
            var currentTrackTitle by remember { mutableStateOf("Ничего не играет") }
            var currentAudioUri by remember { mutableStateOf<Uri?>(null) }
            var currentLyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
            var currentAlbumArt by remember { mutableStateOf<Bitmap?>(null) }
            var isPlaying by remember { mutableStateOf(false) }

            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }
            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

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
                    currentTrackTitle = "Аудиофайл"
                    currentAudioUri = uri
                    withContext(Dispatchers.IO) {
                        currentLyrics = loadLyrics(uri)
                        currentAlbumArt = loadAlbumArt(context, uri)
                    }
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
                                currentAudioUri = null
                                currentLyrics = emptyList()
                                currentAlbumArt = null
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
                        repo.saveThemeId(theme.id)
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
                                    NavigationBar(containerColor = currentTheme.surface) {
                                        NavigationBarItem(selected = currentScreen == Screen.Files, onClick = { currentScreen = Screen.Files }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Файлы") }, colors = navColors(currentTheme))
                                        NavigationBarItem(selected = currentScreen == Screen.Playlists, onClick = { currentScreen = Screen.Playlists }, icon = { Icon(Icons.Default.PlaylistPlay, null) }, label = { Text("Плейлисты") }, colors = navColors(currentTheme))
                                        NavigationBarItem(selected = currentScreen == Screen.Settings || currentScreen == Screen.Themes, onClick = { currentScreen = Screen.Settings }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Настройки") }, colors = navColors(currentTheme))
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
                                Screen.Settings -> SettingsScreen(
                                    onOpenThemes = { currentScreen = Screen.Themes },
                                    autoShowLyrics = autoShowLyrics,
                                    onAutoShowLyricsChange = { autoShowLyrics = it; prefs.edit().putBoolean("auto_lyrics", it).apply() },
                                    visualizerStyle = visualizerStyle,
                                    onVisualizerStyleChange = { visualizerStyle = it; prefs.edit().putString("viz_style", it).apply() }
                                )
                                Screen.Themes -> ThemePickerScreen(
                                    current = currentTheme,
                                    allThemes = repo.getAllThemes(),
                                    onSelect = { theme -> currentTheme = theme; repo.saveThemeId(theme.id); currentScreen = Screen.Settings }
                                )
                                Screen.Player -> PlayerScreen(
                                    theme = currentTheme,
                                    player = exoPlayer,
                                    title = currentTrackTitle,
                                    lyrics = currentLyrics,
                                    albumArt = currentAlbumArt,
                                    currentUri = currentAudioUri,
                                    autoShowLyrics = autoShowLyrics,
                                    visualizerStyle = visualizerStyle,
                                    onBack = { currentScreen = Screen.Files }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun navColors(theme: DxTheme) = NavigationBarItemDefaults.colors(
        selectedIconColor = theme.onPrimary, selectedTextColor = theme.primary,
        indicatorColor = theme.primary, unselectedIconColor = theme.onSurface.copy(alpha = 0.6f),
        unselectedTextColor = theme.onSurface.copy(alpha = 0.6f)
    )

    private fun loadAlbumArt(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture
            art?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (e: Exception) { null } finally { retriever.release() }
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
            if (path.endsWith(".dxth") || mime == "application/zip") externalThemeUri = uri
            else if (path.endsWith(".m3u") || mime.contains("mpegurl")) externalPlaylistUri = uri
            else externalAudioUri = uri
        }
    }

    private fun parseM3u(context: Context, uri: Uri): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        items.add(MediaItem.fromUri(Uri.parse(trimmed)))
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
    Surface(color = theme.surface, modifier = Modifier.fillMaxWidth().padding(8.dp).shadow(4.dp, RoundedCornerShape(12.dp)).clickable { onClick() }, shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = theme.primary)
            Spacer(Modifier.width(12.dp))
            Text(text = title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = theme.onSurface)
            IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = theme.primary) }
        }
    }
}

@Composable
fun PlayerScreen(
    theme: DxTheme,
    player: ExoPlayer,
    title: String,
    lyrics: List<LyricLine>,
    albumArt: Bitmap?,
    currentUri: Uri?,
    autoShowLyrics: Boolean,
    visualizerStyle: String,
    onBack: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var duration by remember { mutableStateOf(player.duration) }
    var showLyrics by remember { mutableStateOf(autoShowLyrics && lyrics.isNotEmpty()) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var sliderPosition by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var visualizerData by remember { mutableStateOf(FloatArray(64) { 0f }) }
    var visualizer by remember { mutableStateOf<Visualizer?>(null) }
    val audioSessionId = player.audioSessionId

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<File>>(emptyList()) }
    
    if (showAddToPlaylistDialog) {
        val playlistsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "playlists").apply { mkdirs() }
        playlists = playlistsDir.listFiles { f -> f.name.endsWith(".m3u") }?.toList() ?: emptyList()
        
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("Добавить в плейлист") },
            text = {
                if (playlists.isEmpty()) Text("Нет созданных плейлистов. Создайте их во вкладке Плейлисты.")
                else LazyColumn {
                    items(playlists) { file ->
                        Text(file.nameWithoutExtension, modifier = Modifier.fillMaxWidth().clickable {
                            if (currentUri != null) {
                                file.appendText("${currentUri}\n")
                            }
                            showAddToPlaylistDialog = false
                        }.padding(12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddToPlaylistDialog = false }) { Text("Закрыть") } }
        )
    }

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
            if (!isDragging) sliderPosition = player.currentPosition.toFloat()
            if (player.duration > 0) duration = player.duration
            delay(100)
        }
    }

    LaunchedEffect(sliderPosition, showLyrics) {
        if (showLyrics && lyrics.isNotEmpty()) {
            val currentLineIndex = lyrics.indexOfLast { it.timeMs <= sliderPosition.toLong() }
            if (currentLineIndex >= 0) listState.animateScrollToItem(currentLineIndex)
        }
    }

    DisposableEffect(audioSessionId, visualizerStyle) {
        if (visualizerStyle != "Отключен" && audioSessionId != 0) {
            try {
                val vis = Visualizer(audioSessionId)
                vis.captureSize = Visualizer.getCaptureSizeRange()[1]
                var lastUpdateTime = 0L
                vis.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (visualizerStyle == "Волна" && waveform != null) {
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime > 33) {
                                lastUpdateTime = now
                                visualizerData = FloatArray(waveform.size / 8) { i -> abs(waveform[i * 8].toInt()) / 128f }
                            }
                        }
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (visualizerStyle == "Спектр (Cava)" && fft != null) {
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime > 33) {
                                lastUpdateTime = now
                                visualizerData = FloatArray(32) { i ->
                                    val real = fft[i * 2].toFloat()
                                    val imag = fft[i * 2 + 1].toFloat()
                                    val magnitude = sqrt(real * real + imag * imag) / 128f
                                    magnitude.coerceIn(0f, 1f)
                                }
                            }
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                vis.enabled = true
                visualizer = vis
            } catch (e: Exception) { e.printStackTrace() }
        }
        onDispose { visualizer?.enabled = false; visualizer?.release(); visualizer = null }
    }

    // --- ЖЕСТЫ ---
    var accumulatedDrag by remember { mutableStateOf(Offset.Zero) }
    val gestureModifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { accumulatedDrag = Offset.Zero },
            onDrag = { change, dragAmount -> change.consume(); accumulatedDrag += dragAmount },
            onDragEnd = {
                if (accumulatedDrag.x > 150) player.seekToPrevious()
                else if (accumulatedDrag.x < -150) player.seekToNext()
                else if (accumulatedDrag.y > 150) audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                else if (accumulatedDrag.y < -150) audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                accumulatedDrag = Offset.Zero
            }
        )
    }

    val gradient = Brush.verticalGradient(colors = listOf(theme.primary.copy(alpha = 0.15f), theme.background))

    Column(modifier = Modifier.fillMaxSize().background(gradient).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = theme.onSurface) }
            Text(if (showLyrics) "Текст песни" else "Сейчас играет", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), color = theme.onSurface)
            
            if (currentUri != null) {
                IconButton(onClick = { showAddToPlaylistDialog = true }) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = "В плейлист", tint = theme.onSurface)
                }
            }
            
            if (lyrics.isNotEmpty()) {
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(if (showLyrics) Icons.Default.MusicNote else Icons.Default.Subject, contentDescription = "Текст", tint = theme.onSurface)
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().then(gestureModifier), contentAlignment = Alignment.Center) {
            if (showLyrics && lyrics.isNotEmpty()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 100.dp)) {
                    items(lyrics) { line ->
                        val isCurrent = line.timeMs <= sliderPosition.toLong() && line.timeMs + 5000 > sliderPosition.toLong()
                        Text(line.text, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center, color = if (isCurrent) theme.primary else theme.onSurface, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, fontSize = if (isCurrent) 20.sp else 16.sp)
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(modifier = Modifier.size(260.dp).shadow(12.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), color = theme.surface) {
                        if (albumArt != null) {
                            Image(bitmap = albumArt.asImageBitmap(), contentDescription = "Обложка", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        } else {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(120.dp), tint = theme.primary) }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, color = theme.onSurface)
                }
            }
        }

        if (visualizerStyle != "Отключен") {
            Canvas(modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp)) {
                if (visualizerData.isNotEmpty()) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2
                    if (visualizerStyle == "Волна") {
                        val path = Path()
                        val step = width / (visualizerData.size - 1).coerceAtLeast(1)
                        path.moveTo(0f, centerY)
                        visualizerData.forEachIndexed { index, value -> val x = index * step; val y = centerY - (value * height / 2); if (index == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                        path.lineTo(width, centerY); path.lineTo(0f, centerY); path.close()
                        drawPath(path = path, color = theme.primary.copy(alpha = 0.8f))
                        drawPath(path = path, color = theme.primary, style = Stroke(width = 2.dp.toPx()))
                    } else if (visualizerStyle == "Спектр (Cava)") {
                        val barWidth = width / (visualizerData.size * 2)
                        visualizerData.forEachIndexed { index, value ->
                            val barHeight = value * height
                            val x = index * barWidth * 2
                            drawRoundRect(color = theme.primary, topLeft = Offset(x, centerY - barHeight / 2), size = Size(barWidth, barHeight), cornerRadius = CornerRadius(4f, 4f))
                        }
                    }
                }
            }
        }

        Slider(
            value = sliderPosition.coerceIn(0f, if (duration > 0) duration.toFloat() else 1f),
            onValueChange = { isDragging = true; sliderPosition = it },
            onValueChangeFinished = { isDragging = false; player.seekTo(sliderPosition.toLong()) },
            valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
            colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary, inactiveTrackColor = theme.surface)
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(sliderPosition.toLong()), style = MaterialTheme.typography.bodySmall, color = theme.onSurface)
            Text(formatTime(duration), style = MaterialTheme.typography.bodySmall, color = theme.onSurface)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { player.seekToPrevious() }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(40.dp), tint = theme.onSurface)
            }
            Spacer(modifier = Modifier.width(24.dp))
            Surface(
                modifier = Modifier.size(84.dp).clickable { if (isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }.shadow(8.dp, CircleShape),
                shape = CircleShape, color = theme.primary
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
