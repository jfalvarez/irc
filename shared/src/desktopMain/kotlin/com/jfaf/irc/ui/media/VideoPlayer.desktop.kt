package com.jfaf.irc.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.video_mute
import com.jfaf.irc.resources.video_open_in_browser
import com.jfaf.irc.resources.video_pause
import com.jfaf.irc.resources.video_play
import com.jfaf.irc.resources.video_unmute
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState
import org.jetbrains.compose.resources.stringResource

/**
 * Reproductor de vídeo de escritorio. Si el motor nativo del sistema sabe con el formato
 * ([DesktopVideoSupport]) se reproduce dentro de la app; si no (p. ej. WebM en Windows y macOS), o si el
 * motor falla con él, queda el botón que abre el enlace en el navegador.
 */
@Composable
actual fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier,
    isPreview: Boolean
) {
    if (DesktopVideoSupport.canPlayInApp(videoUrl)) {
        InAppVideoPlayer(videoUrl, modifier, isPreview)
    } else {
        OpenInBrowserVideo(videoUrl, modifier)
    }
}

@Composable
private fun InAppVideoPlayer(videoUrl: String, modifier: Modifier, isPreview: Boolean) {
    val player = rememberVideoPlayerState()

    // Vista previa: sin sonido y en bucle, como en el móvil. A pantalla completa: con sonido y controles.
    LaunchedEffect(player, videoUrl, isPreview) {
        player.loop = isPreview
        player.volume = if (isPreview) 0f else 1f
        player.openUri(videoUrl)
    }

    // El motor del sistema no pudo con él (códec, red, archivo…): se abre en el navegador, que reproduce casi todo
    if (player.error != null) {
        OpenInBrowserVideo(videoUrl, modifier)
        return
    }

    Box(modifier = modifier.background(Color.Black)) {
        VideoPlayerSurface(
            playerState = player,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        ) {
            if (!isPreview) {
                Box(modifier = Modifier.fillMaxSize()) {
                    VideoControls(player, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        if (player.isLoading || !player.hasMedia) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun VideoControls(player: VideoPlayerState, modifier: Modifier = Modifier) {
    var muted by remember { mutableStateOf(false) }
    val textStyle = MaterialTheme.typography.labelSmall

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
            Icon(
                imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (player.isPlaying) Res.string.video_pause else Res.string.video_play),
                tint = Color.White
            )
        }
        Text(player.positionText, color = Color.White, style = textStyle)
        Slider(
            value = player.sliderPos,
            onValueChange = { player.seekStart(it) },
            onValueChangeFinished = { player.seekFinished() },
            valueRange = 0f..1000f, // la posición de la librería va de 0 a 1000
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text(player.durationText, color = Color.White, style = textStyle)
        IconButton(onClick = {
            muted = !muted
            player.volume = if (muted) 0f else 1f
        }) {
            Icon(
                imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(if (muted) Res.string.video_unmute else Res.string.video_mute),
                tint = Color.White
            )
        }
    }
}

/** El recurso de siempre: un botón que abre el vídeo en el navegador. */
@Composable
private fun OpenInBrowserVideo(videoUrl: String, modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "▶ " + stringResource(Res.string.video_open_in_browser),
            color = Color.White,
            modifier = Modifier
                .clickable { uriHandler.openUri(videoUrl) }
                .padding(16.dp)
        )
    }
}
