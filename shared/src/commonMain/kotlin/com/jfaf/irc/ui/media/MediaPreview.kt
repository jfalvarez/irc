package com.jfaf.irc.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jfaf.irc.domain.chat.model.MediaTypeEnum

/** Vista previa de una imagen o un vídeo dentro de un mensaje. */
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
                val context = LocalPlatformContext.current
                // Se recuerda la petición por URL: así no se reinicia un GIF si la URL no cambia.
                val imageRequest = remember(mediaUrl) {
                    ImageRequest.Builder(context)
                        .data(mediaUrl)
                        .memoryCacheKey(mediaUrl)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Image Preview",
                    modifier = Modifier.fillMaxSize()
                )
            }
            MediaTypeEnum.VIDEO -> {
                VideoPlayer(
                    modifier = Modifier.fillMaxSize(),
                    videoUrl = mediaUrl,
                    isPreview = true
                )
            }
            MediaTypeEnum.NONE -> {
                // Nada
            }
        }
    }
}
