package com.dxaudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.material3.Player
import com.dxaudio.theme.DxTheme
import com.dxaudio.theme.DxThemeProvider
import com.dxaudio.theme.ThemeRepository
import com.dxaudio.ui.ThemePickerScreen

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val repo = remember { ThemeRepository(context) }
            var currentTheme by remember { mutableStateOf(repo.defaultTheme()) }
            var showThemes by remember { mutableStateOf(false) }

            DxThemeProvider(currentTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showThemes) {
                        ThemePickerScreen(
                            current = currentTheme,
                            onSelect = { theme ->
                                currentTheme = theme
                                showThemes = false
                            }
                        )
                    } else {
                        // Экран плеера
                        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                            Button(
                                onClick = { showThemes = true },
                                modifier = Modifier.padding(16.dp)
                            ) { Text("Темы") }

                            // Инициализация плеера
                            AndroidViewPlayer(onPlayerReady = { exoPlayer ->
                                player = exoPlayer
                            })
                        }
                    }
                }
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
private fun AndroidViewPlayer(onPlayerReady: (ExoPlayer) -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
            setMediaItem(mediaItem)
            prepare()
        }
    }
    LaunchedEffect(player) { onPlayerReady(player) }
    Player(player = player, modifier = Modifier.fillMaxSize())
    DisposableEffect(Unit) { onDispose { player.release() } }
}
