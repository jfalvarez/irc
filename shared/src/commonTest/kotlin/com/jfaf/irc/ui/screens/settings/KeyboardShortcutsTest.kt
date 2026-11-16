package com.jfaf.irc.ui.screens.settings

import com.jfaf.irc.ui.screens.chat.QUICK_SWITCH_SHORTCUT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** La lista de atajos que enseña Ajustes recoge los que la app atiende de verdad (`chatShortcuts` y el campo de mensajes). */
class KeyboardShortcutsTest {

    private val keys = KeyboardShortcuts.all.map { it.keys }

    @Test
    fun recogeLosAtajosDelChat() {
        listOf(QUICK_SWITCH_SHORTCUT, "Ctrl+Tab", "Ctrl+Mayús+Tab", "Ctrl+W", "Intro", "Tab").forEach {
            assertTrue(it in keys, "Falta el atajo $it en la lista de Ajustes: $keys")
        }
    }

    @Test
    fun sinTeclasRepetidas_yElSelectorRapidoPrimero() {
        assertEquals(keys.size, keys.distinct().size)
        assertEquals(QUICK_SWITCH_SHORTCUT, keys.first())
    }

    @Test
    fun elSelectorRapidoEsCtrlK() {
        assertEquals("Ctrl+K", QUICK_SWITCH_SHORTCUT)
    }
}
