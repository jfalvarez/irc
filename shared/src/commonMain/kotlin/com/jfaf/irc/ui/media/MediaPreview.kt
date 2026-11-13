package com.jfaf.irc.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
                // Los GIF se animan (en escritorio, con GifAnimation; en Android, con Coil).
                MediaImage(
                    url = mediaUrl,
                    contentDescription = "Image Preview",
                    modifier = Modifier.fillMaxSize(),
                    memoryCacheKey = mediaUrl
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
