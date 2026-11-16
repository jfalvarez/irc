package com.jfaf.irc.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.settings_section_shortcuts
import com.jfaf.irc.resources.shortcut_autocomplete
import com.jfaf.irc.resources.shortcut_close_tab
import com.jfaf.irc.resources.shortcut_next_tab
import com.jfaf.irc.resources.shortcut_previous_tab
import com.jfaf.irc.resources.shortcut_quick_switch
import com.jfaf.irc.resources.shortcut_send
import com.jfaf.irc.resources.shortcut_search
import com.jfaf.irc.ui.screens.chat.KeyCap
import com.jfaf.irc.ui.screens.chat.QUICK_SWITCH_SHORTCUT
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Un atajo de teclado del chat: las teclas tal como se muestran y qué hace. */
class KeyboardShortcut(val keys: String, val description: StringResource)

/**
 * Los atajos de teclado de la app (con teclado físico). Es la lista que se enseña en Ajustes: cuando se añada un
 * atajo en `chatShortcuts` o en el campo de mensajes, se añade aquí también.
 */
object KeyboardShortcuts {
    val all: List<KeyboardShortcut> = listOf(
        KeyboardShortcut(QUICK_SWITCH_SHORTCUT, Res.string.shortcut_quick_switch),
        KeyboardShortcut("Ctrl+F", Res.string.shortcut_search),
        KeyboardShortcut("Ctrl+Tab", Res.string.shortcut_next_tab),
        KeyboardShortcut("Ctrl+Mayús+Tab", Res.string.shortcut_previous_tab),
        KeyboardShortcut("Ctrl+W", Res.string.shortcut_close_tab),
        KeyboardShortcut("Intro", Res.string.shortcut_send),
        KeyboardShortcut("Tab", Res.string.shortcut_autocomplete)
    )
}

/** Sección «Atajos de teclado» de Ajustes: una fila por atajo, con la tecla a la izquierda. Sólo en escritorio. */
fun LazyListScope.keyboardShortcutsSection() {
    item {
        Text(
            text = stringResource(Res.string.settings_section_shortcuts),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
    items(KeyboardShortcuts.all.size) { index ->
        val shortcut = KeyboardShortcuts.all[index]
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            KeyCap(shortcut.keys)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(shortcut.description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
