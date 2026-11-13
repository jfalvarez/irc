package com.jfaf.irc.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/** En Android los GIF los anima Coil con `AnimatedImageDecoder` (ver `IrcApplication`). */
actual val platformAnimatesGifs: Boolean = false

@Composable
actual fun AnimatedGif(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    onResult: (animated: Boolean) -> Unit
) {
    // Nunca se usa (platformAnimatesGifs es false); por si acaso, cae al cargador normal.
    onResult(false)
}
