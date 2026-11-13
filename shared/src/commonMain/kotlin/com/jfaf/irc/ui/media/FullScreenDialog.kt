package com.jfaf.irc.ui.media

import androidx.compose.runtime.Composable

/**
 * Diálogo a todo el ancho (sin el margen por defecto de la plataforma) para el visor de medios.
 * Las propiedades de `DialogProperties` que lo permiten son distintas en Android y en escritorio.
 */
@Composable
expect fun FullScreenDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit)
