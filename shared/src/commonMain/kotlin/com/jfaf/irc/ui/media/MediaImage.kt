package com.jfaf.irc.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jfaf.irc.domain.chat.MediaUrl

/** Estado de carga de una imagen, igual para el cargador de Coil y para el GIF animado. */
enum class ImageLoadState { Loading, Success, Error }

/**
 * Imagen de un mensaje. Los GIF se animan donde la plataforma lo hace por su cuenta ([platformAnimatesGifs], escritorio);
 * el resto, y cualquier GIF que no se pueda animar, lo pinta Coil (en Android también animado, con su decodificador).
 */
@Composable
fun MediaImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    /** Se recuerda la petición por esta clave: así no se reinicia un GIF si la URL no cambia. */
    memoryCacheKey: String? = null,
    onState: (ImageLoadState) -> Unit = {}
) {
    val latestOnState by rememberUpdatedState(onState)
    // Si animar falla (red, formato), se vuelve al cargador normal, que lo pinta fijo.
    var animationFailed by remember(url) { mutableStateOf(false) }

    if (platformAnimatesGifs && MediaUrl.isGif(url) && !animationFailed) {
        LaunchedEffect(url) { latestOnState(ImageLoadState.Loading) }
        AnimatedGif(url, contentDescription, modifier, contentScale) { animated ->
            if (animated) latestOnState(ImageLoadState.Success) else animationFailed = true
        }
    } else {
        val context = LocalPlatformContext.current
        val request = remember(url, memoryCacheKey) {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey(memoryCacheKey)
                .crossfade(true)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            onState = { state -> latestOnState(state.toLoadState()) }
        )
    }
}

private fun AsyncImagePainter.State.toLoadState() = when (this) {
    is AsyncImagePainter.State.Success -> ImageLoadState.Success
    is AsyncImagePainter.State.Error -> ImageLoadState.Error
    else -> ImageLoadState.Loading // Empty y Loading
}
