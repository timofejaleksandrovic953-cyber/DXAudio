package com.dxaudio

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dxaudio.theme.DxTheme
import com.dxaudio.theme.DxThemeProvider
import com.dxaudio.theme.ThemeRepository
import com.dxaudio.ui.ThemePickerScreen
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LyricLine(val timeMs: Long, val text: String)
data class AudioMetadata(val title: String, val artist: String, val album: String, val bitmap: Bitmap?)

sealed class Screen {
    object Files : Screen()
    object Playlists : Screen()
    object Library : Screen()
    object Settings : Screen()
    object Themes : Screen()
    object Player : Screen()
}

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var externalAudioUri by mutableStateOf<Uri?>(null)
    private var externalThemeUri by mutableStateOf<Uri?>(null)
    private var externalPlaylistUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        setContent {
            val context = LocalContext.current
            val repo = remember { ThemeRepository(context) }
            val prefs = remember { context.getSharedPreferences("dx_prefs", Context.MODE_PRIVATE) }
            
            var mediaController by remember { mutableStateOf<MediaController?>(null) }
            var currentTheme by remember { mutableStateOf(repo.getSavedTheme()) }
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Files) }
            
            var autoShowLyrics by remember { mutableStateOf(prefs.getBoolean("auto_lyrics", false)) }
            var visualizerStyle by remember { mutableStateOf(prefs.getString("viz_style", "Волна") ?: "Волна") }
            var visualizerColor by remember { mutableStateOf(prefs.getString("viz_color", "Цвет темы") ?: "Цвет темы") }
            
            var currentMetadata by remember { mutableStateOf<AudioMetadata?>(null) }
            var currentLyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
            var isPlaying by remember { mutableStateOf(false) }

            var showWhatsNew by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!prefs.getBoolean("whats_new_v2", false)) {
                    showWhatsNew = true
                }
            }
            if (showWhatsNew) {
                AlertDialog(
                    onDismissRequest = { showWhatsNew = false; prefs.edit().putBoolean("whats_new_v2", true).apply() },
                    title = { Text("🎉 DXAudio 2.0!") },
                    text = { Text("Что нового:\n\n• Плавные переходы и новый дизайн плеера\n• Виджет на рабочий стол\n• Избранное и история\n• Очередь воспроизведения\n• Поиск и сортировка в файлах\n• Плейлисты в виде сетки") },
                    confirmButton = { TextButton(onClick = { showWhatsNew = false; prefs.edit().putBoolean("whats_new_v2", true).apply() }) { Text("Круто!") } }
                )
            }

            LaunchedEffect(Unit) {
                controllerFuture?.addListener({
                    try { mediaController = controllerFuture?.get() } catch (e: Exception) { e.printStackTrace() }
                }, ContextCompat.getMainExecutor(context))
            }

            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }
            LaunchedEffect(Unit) {
                val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                permissionLauncher.launch(permissions.toTypedArray())
            }

            LaunchedEffect(mediaController) {
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.mediaId?.let { uriStr ->
                            val uri = Uri.parse(uriStr)
                            FavoritesRepository.addToHistory(context, uri)
                            CoroutineScope(Dispatchers.IO).launch {
                                val lyrics = loadLyrics(context, uri)
                                val meta = loadMetadata(context, uri)
                                withContext(Dispatchers.Main) {
                                    currentLyrics = lyrics
                                    currentMetadata = meta
                                }
                            }
                        }
                    }
                })
            }

            LaunchedEffect(externalAudioUri) {
                externalAudioUri?.let { uri ->
                    mediaController?.setMediaItem(MediaItem.Builder().setUri(uri).setMediaId(uri.toString()).build())
                    mediaController?.prepare()
                    mediaController?.play()
                    withContext(Dispatchers.IO) {
                        currentLyrics = loadLyrics(context, uri)
                        currentMetadata = loadMetadata(context, uri)
                    }
                    FavoritesRepository.addToHistory(context, uri)
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
                                mediaController?.setMediaItems(mediaItems)
                                mediaController?.prepare()
                                mediaController?.play()
                                currentLyrics = emptyList()
                                currentMetadata = AudioMetadata("Плейлист", "", "", null)
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
                    if (theme != null) { currentTheme = theme; repo.saveThemeId(theme.id) }
                    externalThemeUri = null
                }
            }

            if (mediaController != null) {
                DxThemeProvider(currentTheme) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Scaffold(
                            bottomBar = {
                                if (currentScreen != Screen.Player) {
                                    Column {
                                        if (currentMetadata != null) {
                                            val progress = if ((mediaController?.duration ?: 0L) > 0L) (mediaController?.currentPosition?.toFloat() ?: 0f) / mediaController!!.duration.toFloat() else 0f
                                            MiniPlayer(currentTheme, currentMetadata!!.title, progress, isPlaying, { if (isPlaying) mediaController?.pause() else mediaController?.play() }, { currentScreen = Screen.Player })
                                        }
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            NavigationBar(
                                                modifier = Modifier.clip(RoundedCornerShape(24.dp)).shadow(8.dp, RoundedCornerShape(24.dp)),
                                                containerColor = currentTheme.surface.copy(alpha = 0.95f),
                                                contentColor = currentTheme.onSurface
                                            ) {
                                                NavigationBarItem(selected = currentScreen == Screen.Files, onClick = { currentScreen = Screen.Files }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Файлы") }, colors = navColors(currentTheme))
                                                NavigationBarItem(selected = currentScreen == Screen.Playlists, onClick = { currentScreen = Screen.Playlists }, icon = { Icon(Icons.Default.PlaylistPlay, null) }, label = { Text("Плейлисты") }, colors = navColors(currentTheme))
                                                NavigationBarItem(selected = currentScreen == Screen.Library, onClick = { currentScreen = Screen.Library }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Библиотека") }, colors = navColors(currentTheme))
                                                NavigationBarItem(selected = currentScreen == Screen.Settings || currentScreen == Screen.Themes, onClick = { currentScreen = Screen.Settings }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Настройки") }, colors = navColors(currentTheme))
                                            }
                                        }
                                    }
                                }
                            }
                        ) { paddingValues ->
                            Box(modifier = Modifier.padding(paddingValues)) {
                                AnimatedContent(
                                    targetState = currentScreen,
                                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                                    label = "screen_transition"
                                ) { screen ->
                                    when (screen) {
                                        Screen.Files -> FileManagerScreen(
                                            onPlayAudio = { uri, _ -> externalAudioUri = uri },
                                            onPlayPlaylist = { uri, _ -> externalPlaylistUri = uri }
                                        )
                                        Screen.Playlists -> PlaylistsScreen(onPlayAudio = { uri, _ -> externalAudioUri = uri })
                                        Screen.Library -> LibraryScreen(onPlayAudio = { uri, _ -> externalAudioUri = uri })
                                        Screen.Settings -> SettingsScreen(
                                            onOpenThemes = { currentScreen = Screen.Themes },
                                            autoShowLyrics = autoShowLyrics,
                                            onAutoShowLyricsChange = { autoShowLyrics = it; prefs.edit().putBoolean("auto_lyrics", it).apply() },
                                            visualizerStyle = visualizerStyle,
                                            onVisualizerStyleChange = { visualizerStyle = it; prefs.edit().putString("viz_style", it).apply() },
                                            visualizerColor = visualizerColor,
                                            onVisualizerColorChange = { visualizerColor = it; prefs.edit().putString("viz_color", it).apply() }
                                        )
                                        Screen.Themes -> ThemePickerScreen(
                                            current = currentTheme,
                                            allThemes = repo.getAllThemes(),
                                            onSelect = { theme -> currentTheme = theme; repo.saveThemeId(theme.id); currentScreen = Screen.Settings }
                                        )
                                        Screen.Player -> PlayerScreen(
                                            theme = currentTheme,
                                            player = mediaController!!,
                                            metadata = currentMetadata,
                                            lyrics = currentLyrics,
                                            autoShowLyrics = autoShowLyrics,
                                            visualizerStyle = visualizerStyle,
                                            visualizerColor = visualizerColor,
                                            onBack = { currentScreen = Screen.Files }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }

    @Composable
    private fun navColors(theme: DxTheme) = NavigationBarItemDefaults.colors(
        selectedIconColor = theme.onPrimary, selectedTextColor = theme.primary,
        indicatorColor = theme.primary, unselectedIconColor = theme.onSurface.copy(alpha = 0.6f),
        unselectedTextColor = theme.onSurface.copy(alpha = 0.6f)
    )

    private fun loadLyrics(context: Context, audioUri: Uri): List<LyricLine> {
        return try {
            val doc = DocumentFile.fromSingleUri(context, audioUri) ?: return emptyList()
            val parent = doc.parentFile ?: return emptyList()
            val baseName = doc.name?.substringBeforeLast(".") ?: return emptyList()
            val lrcDoc = parent.findFile("$baseName.lrc") ?: return emptyList()

            val lyrics = mutableListOf<LyricLine>()
            val regex = Regex("\\[(\\d{2}):(\\d{2}\\.\\d{2})](.*)")
            context.contentResolver.openInputStream(lrcDoc.uri)?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    val match = regex.find(line)
                    if (match != null) {
                        val min = match.groupValues[1].toLong()
                        val sec = match.groupValues[2].toDouble()
                        val text = match.groupValues[3].trim()
                        if (text.isNotEmpty()) lyrics.add(LyricLine((min * 60 * 1000) + (sec * 1000).toLong(), text))
                    }
                }
            }
            lyrics.sortedBy { it.timeMs }
        } catch (e: Exception) { emptyList() }
    }

    private fun loadMetadata(context: Context, uri: Uri): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Неизвестно"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Неизвестный исполнитель"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Неизвестный альбом"
            val art = retriever.embeddedPicture
            AudioMetadata(title, artist, album, art?.let { BitmapFactory.decodeByteArray(it, 0, it.size) })
        } catch (e: Exception) {
            AudioMetadata(uri.lastPathSegment ?: "Трек", "", "", null)
        } finally { retriever.release() }
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
                        val itemUri = Uri.parse(trimmed)
                        items.add(MediaItem.Builder().setUri(itemUri).setMediaId(itemUri.toString()).build())
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return items
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

@Composable
fun LibraryScreen(onPlayAudio: (Uri, String) -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    
    val favorites = remember { FavoritesRepository.getFavorites(context) }
    val history = remember { FavoritesRepository.getFavoritesHistory(context) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Избранное") }, icon = { Icon(Icons.Default.Favorite, null) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("История") }, icon = { Icon(Icons.Default.History, null) })
        }
        
        val currentList = if (selectedTab == 0) favorites else history
        if (currentList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (selectedTab == 0) "Нет избранных треков" else "История пуста") }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(currentList) { uri ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPlayAudio(uri, uri.lastPathSegment ?: "Трек") }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Text(uri.lastPathSegment?.substringAfterLast("/") ?: "Неизвестный трек", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(theme: DxTheme, title: String, progress: Float, isPlaying: Boolean, onPlayPause: () -> Unit, onClick: () -> Unit) {
    Surface(color = theme.surface, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).shadow(4.dp, RoundedCornerShape(12.dp)).clickable { onClick() }, shape = RoundedCornerShape(12.dp)) {
        Column {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = theme.primary)
                Spacer(Modifier.width(12.dp))
                Text(text = title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = theme.onSurface, style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = theme.primary) }
            }
            // ИСПРАВЛЕНО: Лямбда для progress и theme.progressBg для фона
            LinearProgressIndicator(
                progress = { progress },
                color = theme.primary,
                trackColor = theme.progressBg,
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    theme: DxTheme,
    player: Player,
    metadata: AudioMetadata?,
    lyrics: List<LyricLine>,
    autoShowLyrics: Boolean,
    visualizerStyle: String,
    visualizerColor: String,
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
    
    val audioSessionId by PlayerHolder.audioSessionId.collectAsState()

    var targetData by remember { mutableStateOf(FloatArray(64) { 0f }) }
    var smoothData by remember { mutableStateOf(FloatArray(64) { 0f }) }
    var visualizer by remember { mutableStateOf<Visualizer?>(null) }
    var isVisInit by remember { mutableStateOf(false) }

    var isFavorite by remember { mutableStateOf(FavoritesRepository.isFavorite(context, player.currentMediaItem?.localConfiguration?.uri)) }
    var showQueueSheet by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val playScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    LaunchedEffect(player.currentMediaItem) {
        isFavorite = FavoritesRepository.isFavorite(context, player.currentMediaItem?.localConfiguration?.uri)
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

    LaunchedEffect(Unit) {
        while (true) {
            smoothData = FloatArray(64) { i -> 
                val target = if (i < targetData.size) targetData[i] else 0f
                smoothData[i] + (target - smoothData[i]) * 0.2f
            }
            delay(16)
        }
    }

    LaunchedEffect(sliderPosition, showLyrics) {
        if (showLyrics && lyrics.isNotEmpty()) {
            val currentLineIndex = lyrics.indexOfLast { it.timeMs <= sliderPosition.toLong() }
            if (currentLineIndex >= 0) listState.animateScrollToItem(currentLineIndex)
        }
    }

    DisposableEffect(audioSessionId, visualizerStyle) {
        if (visualizerStyle != "Отключен") {
            val initVis = {
                val currentSessionId = player.audioSessionId
                if (currentSessionId != 0) {
                    try {
                        val vis = Visualizer(currentSessionId)
                        vis.captureSize = Visualizer.getCaptureSizeRange()[1]
                        vis.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                                if (visualizerStyle != "Спектр (Cava)" && visualizerStyle != "Круг" && visualizerStyle != "Зеркальные бары" && visualizerStyle != "Точки" && waveform != null) {
                                    targetData = FloatArray(waveform.size / 8) { i -> abs(waveform[i * 8].toInt()) / 128f }
                                }
                            }
                            override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                                if (fft != null) {
                                    targetData = FloatArray(64) { i ->
                                        val real = fft[i * 2].toFloat()
                                        val imag = fft[i * 2 + 1].toFloat()
                                        (sqrt(real * real + imag * imag) / 128f).coerceIn(0f, 1f)
                                    }
                                }
                            }
                        }, Visualizer.getMaxCaptureRate(), true, true)
                        vis.enabled = true
                        visualizer = vis
                        isVisInit = true
                    } catch (e: Exception) { Log.e("Visualizer", "Init failed", e) }
                }
            }
            initVis()
            val handler = Handler(Looper.getMainLooper())
            val runnable = Runnable { if (!isVisInit) initVis() }
            handler.postDelayed(runnable, 1500)

            onDispose { 
                handler.removeCallbacks(runnable)
                visualizer?.enabled = false
                visualizer?.release()
                visualizer = null
                isVisInit = false
            }
        } else {
            onDispose { }
        }
    }

    val gestureModifier = Modifier.pointerInput(Unit) {
        var dragAccumulator = Offset.Zero
        detectDragGestures(
            onDragStart = { dragAccumulator = Offset.Zero },
            onDrag = { change, dragAmount -> change.consume(); dragAccumulator += dragAmount },
            onDragEnd = {
                if (dragAccumulator.x > 150) player.seekToPrevious()
                else if (dragAccumulator.x < -150) player.seekToNext()
                else if (dragAccumulator.y > 150) audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                else if (dragAccumulator.y < -150) audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
        )
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(Color.Black.copy(alpha = 0.3f), theme.background)
    )
    val vizColor = when (visualizerColor) {
        "Радуга" -> Color.hsv((sliderPosition / 1000) % 360f, 1f, 1f)
        "Белый" -> Color.White
        else -> theme.primary
    }

    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }, containerColor = theme.surface) {
            Text("Очередь", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp), color = theme.onSurface)
            LazyColumn(modifier = Modifier.fillMaxHeight(0.8f)) {
                items(player.mediaItemCount) { index ->
                    val item = player.getMediaItemAt(index)
                    val isCurrent = index == player.currentMediaItemIndex
                    Row(
                        Modifier.fillMaxWidth().clickable { player.seekTo(index, 0); showQueueSheet = false }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.mediaId.substringAfterLast("/"), color = if (isCurrent) theme.primary else theme.onSurface, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (metadata?.bitmap != null) {
            Image(
                bitmap = metadata.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(50.dp).alpha(0.4f)
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(gradient))

        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = theme.onSurface) }
                Text(if (showLyrics) "Текст песни" else "Сейчас играет", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), color = theme.onSurface)
                
                IconButton(onClick = { showQueueSheet = true }) { Icon(Icons.Default.QueueMusic, contentDescription = "Очередь", tint = theme.onSurface) }
                IconButton(onClick = { 
                    player.currentMediaItem?.localConfiguration?.uri?.let { uri ->
                        isFavorite = FavoritesRepository.toggleFavorite(context, uri)
                    }
                }) {
                    Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "В избранное", tint = if (isFavorite) theme.primary else theme.onSurface)
                }
                if (lyrics.isNotEmpty()) {
                    IconButton(onClick = { showLyrics = !showLyrics }) { Icon(if (showLyrics) Icons.Default.MusicNote else Icons.Default.Subject, contentDescription = "Текст", tint = theme.onSurface) }
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
                        Surface(modifier = Modifier.size(260.dp).shadow(12.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), color = theme.surface.copy(alpha = 0.8f)) {
                            if (metadata?.bitmap != null) {
                                Image(bitmap = metadata.bitmap.asImageBitmap(), contentDescription = "Обложка", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                            } else {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(120.dp), tint = theme.primary) }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(text = metadata?.title ?: "Неизвестно", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, color = theme.onSurface)
                        Text(text = "${metadata?.artist} • ${metadata?.album}", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, color = theme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }

            if (visualizerStyle != "Отключен") {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    if (!isVisInit) {
                        Text("Ожидание аудио...", style = MaterialTheme.typography.bodySmall, color = theme.onSurface.copy(alpha = 0.5f))
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (smoothData.isNotEmpty() && isVisInit) {
                            val width = size.width; val height = size.height; val centerY = height / 2
                            when (visualizerStyle) {
                                "Волна" -> {
                                    val path = Path(); val step = width / (smoothData.size - 1).coerceAtLeast(1)
                                    path.moveTo(0f, centerY)
                                    smoothData.forEachIndexed { index, value -> val x = index * step; val y = centerY - (value * height / 2); if (index == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                                    path.lineTo(width, centerY); path.lineTo(0f, centerY); path.close()
                                    drawPath(path = path, color = vizColor.copy(alpha = 0.8f)); drawPath(path = path, color = vizColor, style = Stroke(width = 2.dp.toPx()))
                                }
                                "Спектр (Cava)" -> {
                                    val barWidth = width / (smoothData.size * 2)
                                    smoothData.forEachIndexed { index, value -> val barHeight = value * height; val x = index * barWidth * 2; drawRoundRect(color = vizColor, topLeft = Offset(x, centerY - barHeight / 2), size = Size(barWidth, barHeight), cornerRadius = CornerRadius(4f, 4f)) }
                                }
                                "Круг" -> {
                                    val radius = height / 2.5f; val center = Offset(width / 2, centerY)
                                    smoothData.forEachIndexed { index, value ->
                                        val angle = (index.toFloat() / smoothData.size) * 360f; val barLength = value * height; val rad = Math.toRadians(angle.toDouble())
                                        val start = Offset(center.x + (radius * cos(rad)).toFloat(), center.y + (radius * sin(rad)).toFloat())
                                        val end = Offset(center.x + ((radius + barLength) * cos(rad)).toFloat(), center.y + ((radius + barLength) * sin(rad)).toFloat())
                                        drawLine(color = vizColor, start = start, end = end, strokeWidth = 3.dp.toPx())
                                    }
                                }
                                "Зеркальные бары" -> {
                                    val barWidth = width / (smoothData.size * 2)
                                    smoothData.forEachIndexed { index, value ->
                                        val barHeight = value * height / 2; val x = index * barWidth * 2
                                        drawRoundRect(color = vizColor, topLeft = Offset(x, centerY - barHeight), size = Size(barWidth, barHeight), cornerRadius = CornerRadius(2f, 2f))
                                        drawRoundRect(color = vizColor, topLeft = Offset(x, centerY), size = Size(barWidth, barHeight), cornerRadius = CornerRadius(2f, 2f))
                                    }
                                }
                                "Заполненная волна" -> {
                                    val path = Path(); val step = width / (smoothData.size - 1).coerceAtLeast(1)
                                    path.moveTo(0f, height)
                                    smoothData.forEachIndexed { index, value -> val x = index * step; val y = centerY - (value * height / 2); path.lineTo(x, y) }
                                    path.lineTo(width, height); path.close()
                                    drawPath(path = path, color = vizColor.copy(alpha = 0.6f)); drawPath(path = path, color = vizColor, style = Stroke(width = 2.dp.toPx()))
                                }
                                "Точки" -> {
                                    val step = width / (smoothData.size - 1).coerceAtLeast(1)
                                    smoothData.forEachIndexed { index, value -> val x = index * step; val y = centerY - (value * height / 2); drawCircle(color = vizColor, radius = (value * 4.dp.toPx()) + 2f, center = Offset(x, y)) }
                                }
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

            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = theme.surface.copy(alpha = 0.9f),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { player.seekToPrevious() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(32.dp), tint = theme.onSurface)
                        }
                        Surface(
                            modifier = Modifier.size(72.dp).scale(playScale),
                            shape = CircleShape,
                            color = theme.primary
                        ) {
                            IconButton(onClick = { if (isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(40.dp), tint = theme.onPrimary)
                            }
                        }
                        IconButton(onClick = { player.seekToNext() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Следующий", modifier = Modifier.size(32.dp), tint = theme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
