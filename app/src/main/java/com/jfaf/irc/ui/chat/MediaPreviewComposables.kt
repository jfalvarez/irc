package com.jfaf.irc.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder // Added import
import coil.request.ImageRequest     // Added import
import com.jfaf.irc.ui.viewmodels.MediaTypeEnum

@Composable
fun MediaPreview(
    modifier: Modifier = Modifier,
    mediaUrl: String?,
    mediaType: MediaTypeEnum
) {
    if (mediaUrl.isNullOrBlank()) {
        // No hay URL, no mostrar nada o un placeholder si lo deseas
        return
    }

    Box(modifier = modifier) {
        when (mediaType) {
            MediaTypeEnum.IMAGE -> {
                val context = LocalContext.current // Get context for ImageRequest.Builder
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(mediaUrl)
                        .decoderFactory(ImageDecoderDecoder.Factory()) // For animated GIFs (API 28+)
                        .crossfade(true) // Optional: for smooth transition
                        .build(),
                    contentDescription = "Image Preview", // Considera descripciones dinámicas
                    modifier = Modifier.fillMaxSize()
                    // Aquí puedes añadir más configuraciones de Coil si es necesario
                    // (placeholder, error, contentScale, etc.)
                )
            }
            MediaTypeEnum.VIDEO -> {
                VideoPlayer(
                    modifier = Modifier.fillMaxSize(),
                    videoUrl = mediaUrl
                )
            }
            MediaTypeEnum.NONE -> {
                // No es un tipo de medio reconocido, o la URL no es válida
                // Puedes mostrar un placeholder o nada
            }
        }
    }
}

@Composable
fun VideoPlayer(
    modifier: Modifier = Modifier,
    videoUrl: String
) {
    val context = LocalContext.current

    val exoPlayer = remember(videoUrl) { // Keyed remember for ExoPlayer instance recreation on videoUrl change
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true       // Autoplay
            volume = 0f                // Sin sonido para la previsualización
            repeatMode = Player.REPEAT_MODE_ONE // Reproducir en bucle
        }
    }

    DisposableEffect(exoPlayer) { // Keyed DisposableEffect to manage player lifecycle with instance
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false // Sin controles para la previsualización
                // Es importante que PlayerView no interfiera con otros gestos de la UI
                // this.controllerAutoShow = false
            }
        }
    )
}
