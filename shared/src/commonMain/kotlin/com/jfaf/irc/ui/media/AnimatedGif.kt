package com.jfaf.irc.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * Si la plataforma anima los GIF con [AnimatedGif]. En Android no hace falta: el `ImageLoader` de `IrcApplication`
 * ya lleva `AnimatedImageDecoder`. En escritorio Coil 3 no tiene decodificador animado, así que los anima la app.
 */
expect val platformAnimatesGifs: Boolean

/**
 * Descarga y reproduce un GIF animado (sólo donde [platformAnimatesGifs] es `true`).
 * [onResult] recibe `true` al pintarse el primer fotograma y `false` si no se pudo descargar o decodificar;
 * entonces [MediaImage] vuelve al cargador normal, que lo pinta fijo.
 */
@Composable
expect fun AnimatedGif(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    onResult: (animated: Boolean) -> Unit
)
