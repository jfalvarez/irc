package com.jfaf.irc.ui.viewmodels

import com.jfaf.irc.ui.model.UiChatMessage
import com.jfaf.irc.ui.model.UiMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiMessageFilterTest {

    private val all = MessageFilterOptions(
        showJoinPartQuit = true, showNickChanges = true, showModeChanges = true, ignoredUsers = emptySet()
    )

    private fun msg(type: UiMessageType, text: String = "x", sender: String? = null) =
        UiChatMessage(fullText = text, type = type, sender = sender)

    @Test
    fun ignorados_seFiltranSinImportarMayusculas() {
        val opts = all.copy(ignoredUsers = setOf("Pepe"))
        assertFalse(UiMessageFilter.shouldDisplay(msg(UiMessageType.CHANNEL_MSG_RECEIVED, sender = "pEPE"), opts))
        assertFalse(UiMessageFilter.shouldDisplay(msg(UiMessageType.ACTION_MSG, sender = "pepe"), opts))
        assertTrue(UiMessageFilter.shouldDisplay(msg(UiMessageType.CHANNEL_MSG_RECEIVED, sender = "ana"), opts))
    }

    @Test
    fun ignorados_noAfectanAMensajesPropiosNiDeSistema() {
        val opts = all.copy(ignoredUsers = setOf("pepe"))
        assertTrue(UiMessageFilter.shouldDisplay(msg(UiMessageType.CHANNEL_MSG_SENT, sender = "pepe"), opts))
        assertTrue(UiMessageFilter.shouldDisplay(msg(UiMessageType.NOTICE, sender = "pepe"), opts))
    }

    @Test
    fun joinPartQuit_ocultoPorTipoOPorTexto() {
        val opts = all.copy(showJoinPartQuit = false)
        assertFalse(UiMessageFilter.shouldDisplay(msg(UiMessageType.JOIN_PART_QUIT), opts))
        assertFalse(UiMessageFilter.shouldDisplay(msg(UiMessageType.SERVER_INFO, "Bob has Signed Off"), opts))
        assertFalse(UiMessageFilter.shouldDisplay(msg(UiMessageType.SERVER_INFO, "Connection closed"), opts))
        assertTrue(UiMessageFilter.shouldDisplay(msg(UiMessageType.SERVER_INFO, "hola"), opts))
    }

    @Test
    fun nickYModeChanges_respetanSusPreferencias() {
        val opts = all.copy(showNickChanges = false, showModeChanges = false)
        assertFalse(UiMessageFilter.shouldDisplay(msg(UiMessageType.NICK_CHANGE), opts))
        assertFalse(UiMessageFilter.shouldDisplay(msg(UiMessageType.MODE_CHANGE), opts))
        assertTrue(UiMessageFilter.shouldDisplay(msg(UiMessageType.NICK_CHANGE), all))
    }

    @Test
    fun anuncio_seInsertaEnLaPosicion5SoloSiHayMasDe5Mensajes() {
        val six = List(6) { msg(UiMessageType.NOTICE, "m$it") }
        val withAd = UiMessageFilter.apply(six, hasAd = true, options = all)
        assertEquals(7, withAd.size)
        assertEquals(UiMessageType.AD_MESSAGE, withAd[5].type)

        val five = six.take(5)
        assertEquals(5, UiMessageFilter.apply(five, hasAd = true, options = all).size)
        assertEquals(6, UiMessageFilter.apply(six, hasAd = false, options = all).size)
    }
}
