package com.jfaf.irc.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Atajos de teclado del chat (con teclado físico: escritorio, o un teclado Bluetooth en Android):
 * Ctrl+Tab pestaña siguiente, Ctrl+Mayús+Tab pestaña anterior, Ctrl+W cierra la pestaña activa.
 *
 * Usa la fase de vista previa, así que los atajos llegan aunque el foco esté en el campo de texto.
 */
fun Modifier.chatShortcuts(
    onNextTab: () -> Unit,
    onPreviousTab: () -> Unit,
    onCloseTab: () -> Unit
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
        false
    } else when (event.key) {
        Key.Tab -> {
            if (event.isShiftPressed) onPreviousTab() else onNextTab()
            true
        }
        Key.W -> {
            onCloseTab()
            true
        }
        else -> false
    }
}
