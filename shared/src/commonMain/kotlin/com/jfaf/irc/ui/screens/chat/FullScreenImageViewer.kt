package com.jfaf.irc.ui.screens.chat

import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.*
import com.jfaf.irc.log.Log
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.jfaf.irc.ui.media.FullScreenDialog
import com.jfaf.irc.ui.media.ImageLoadState
import com.jfaf.irc.ui.media.MediaImage
import com.jfaf.irc.ui.media.VideoPlayer
import com.jfaf.irc.domain.chat.model.MediaTypeEnum

@Composable
fun FullScreenImageViewer( 
    mediaUrl: String, 
    mediaType: MediaTypeEnum, 
    onClose: () -> Unit
) {
    Log.d("FullScreenViewer", "Displaying media. Type: $mediaType, URL: $mediaUrl")

    FullScreenDialog(onDismissRequest = onClose) {
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
                    var imageState by remember { mutableStateOf(ImageLoadState.Loading) }
                    // Los GIF se animan también a pantalla completa.
                    MediaImage(
                        url = mediaUrl,
                        contentDescription = stringResource(Res.string.cd_full_screen_image),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .fillMaxHeight(0.85f)
                            .clip(RoundedCornerShape(8.dp))
                            .graphicsLayer {
                                alpha = if (imageState == ImageLoadState.Success) 1f else 0f
                            }
                            .clickable( // Prevent closing dialog when clicking on the image itself
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { /* Consumes the click */ }
                            ),
                        onState = { state ->
                            Log.d("FullScreenViewer", "Image state: $state for URL: $mediaUrl")
                            imageState = state
                        }
                    )

                    when (imageState) {
                        ImageLoadState.Loading -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                        }
                        ImageLoadState.Error -> {
                            Text(
                                text = stringResource(Res.string.error_loading_image),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                                    .padding(16.dp)
                            )
                        }
                        ImageLoadState.Success -> { /* Image loaded */ }
                    }
                }
                MediaTypeEnum.VIDEO -> {
                    VideoPlayer(
                        videoUrl = mediaUrl,
                        isPreview = false,
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .fillMaxHeight(0.85f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { /* Consumes the click */ }
                            )
                    )
                }
                MediaTypeEnum.NONE -> {
                    Text(
                        text = stringResource(Res.string.error_unsupported_media),
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
