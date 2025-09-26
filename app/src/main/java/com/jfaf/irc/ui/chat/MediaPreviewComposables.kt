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
import coil.decode.ImageDecoderDecoder 
import coil.request.ImageRequest     
import com.jfaf.irc.ui.viewmodels.MediaTypeEnum

@Composable
fun MediaPreview(
    modifier: Modifier = Modifier,
    mediaUrl: String?,
    mediaType: MediaTypeEnum
) {
    if (mediaUrl.isNullOrBlank()) {
        return
    }

    Box(modifier = modifier) {
        when (mediaType) {
            MediaTypeEnum.IMAGE -> {
                val context = LocalContext.current 
                // Remember the ImageRequest, keyed by mediaUrl.
                // This helps prevent restarting the GIF if the URL hasn't changed.
                val imageRequest = remember(mediaUrl) {
                    ImageRequest.Builder(context)
                        .data(mediaUrl)
                        .decoderFactory(ImageDecoderDecoder.Factory()) 
                        .memoryCacheKey(mediaUrl) // Explicit memory cache key
                        .crossfade(true) 
                        .build()
                }
                AsyncImage(
                    model = imageRequest, // Use the remembered request
                    contentDescription = "Image Preview", 
                    modifier = Modifier.fillMaxSize()
                )
            }
            MediaTypeEnum.VIDEO -> {
                VideoPlayer(
                    modifier = Modifier.fillMaxSize(),
                    videoUrl = mediaUrl
                )
            }
            MediaTypeEnum.NONE -> {
                // Placeholder or nothing
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

    val exoPlayer = remember(videoUrl) { 
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true       
            volume = 0f                
            repeatMode = Player.REPEAT_MODE_ONE 
        }
    }

    DisposableEffect(exoPlayer) { 
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false 
            }
        }
    )
}
