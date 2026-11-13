package com.jfaf.irc.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Reproductor de vídeo de plataforma.
 *
 * @param isPreview `true` en la vista previa del mensaje (sin sonido, en bucle y sin controles);
 * `false` a pantalla completa (con sonido y controles).
 *
 * Android: Media3 (ExoPlayer). Escritorio: de momento un botón que abre el enlace en el navegador.
 */
@Composable
expect fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    isPreview: Boolean
)
