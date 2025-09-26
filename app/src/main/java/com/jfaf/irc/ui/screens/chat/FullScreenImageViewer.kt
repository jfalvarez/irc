package com.jfaf.irc.ui.screens.chat

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.ImageDecoderDecoder // Added import
import coil.request.ImageRequest     // Added import
import com.jfaf.irc.R
import com.jfaf.irc.ui.viewmodels.MediaTypeEnum

@Composable
fun FullScreenImageViewer( 
    mediaUrl: String, 
    mediaType: MediaTypeEnum, 
    onClose: () -> Unit
) {
    Log.d("FullScreenViewer", "Displaying media. Type: $mediaType, URL: $mediaUrl")

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose // Click on background closes the dialog
                ),
            contentAlignment = Alignment.Center
        ) {
            when (mediaType) {
                MediaTypeEnum.IMAGE -> {
                    val context = LocalContext.current // Get context for ImageRequest
                    var imagePainterState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mediaUrl)
                            .decoderFactory(ImageDecoderDecoder.Factory()) // For animated GIFs
                            .crossfade(true) // Optional smooth transition
                            .build(), 
                        contentDescription = stringResource(R.string.cd_full_screen_image),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .fillMaxHeight(0.85f)
                            .clip(RoundedCornerShape(8.dp))
                            .graphicsLayer { 
                                alpha = if (imagePainterState is AsyncImagePainter.State.Success) 1f else 0f
                            }
                            .clickable( // Prevent closing dialog when clicking on the image itself
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { /* Consumes the click */ }
                            ),
                        onState = { state ->
                            Log.d("FullScreenViewer", "AsyncImage State: $state for URL: $mediaUrl") 
                            imagePainterState = state 
                        }
                    )

                    when (val currentState = imagePainterState) {
                        is AsyncImagePainter.State.Loading -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                        }
                        is AsyncImagePainter.State.Error -> {
                            Text(
                                text = stringResource(R.string.error_loading_image),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                                    .padding(16.dp)
                            )
                        }
                        is AsyncImagePainter.State.Success -> { /* Image loaded */ }
                        is AsyncImagePainter.State.Empty -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                MediaTypeEnum.VIDEO -> {
                    val context = LocalContext.current
                    val exoPlayer = remember(mediaUrl) { 
                        ExoPlayer.Builder(context).build().apply {
                            val mediaItem = MediaItem.fromUri(mediaUrl)
                            setMediaItem(mediaItem)
                            prepare()
                            playWhenReady = true       
                            volume = 1f                
                            repeatMode = Player.REPEAT_MODE_OFF 
                        }
                    }

                    DisposableEffect(exoPlayer) { 
                        onDispose {
                            exoPlayer.release()
                        }
                    }

                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .fillMaxHeight(0.85f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable( 
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { /* Consumes the click */ }
                            ),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true 
                            }
                        }
                    )
                }
                MediaTypeEnum.NONE -> {
                    Text(
                        text = stringResource(R.string.error_unsupported_media),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}
