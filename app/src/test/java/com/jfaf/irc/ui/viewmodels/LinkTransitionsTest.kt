package com.jfaf.irc.ui.viewmodels

import com.jfaf.irc.ui.viewmodels.LinkState.CONNECTED
import com.jfaf.irc.ui.viewmodels.LinkState.DISCONNECTED
import com.jfaf.irc.ui.viewmodels.LinkState.RECONNECTING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTransitionsTest {

    @Test
    fun of_dasPrioridadAConectado() {
        assertEquals(CONNECTED, LinkState.of(connected = true, reconnecting = true))
        assertEquals(RECONNECTING, LinkState.of(connected = false, reconnecting = true))
        assertEquals(DISCONNECTED, LinkState.of(connected = false, reconnecting = false))
    }

    @Test
    fun primeraConexion_avisaYReinicia() {
        assertEquals("Conectado al servidor.", LinkTransitions.noticeFor(null, CONNECTED)?.text)
        assertEquals("Conectado al servidor.", LinkTransitions.noticeFor(DISCONNECTED, CONNECTED)?.text)
        assertTrue(LinkTransitions.isFreshConnection(null, CONNECTED))
        assertTrue(LinkTransitions.isFreshConnection(DISCONNECTED, CONNECTED))
    }

    @Test
    fun reconexion_noReiniciaElEstado() {
        assertEquals("Reconectado.", LinkTransitions.noticeFor(RECONNECTING, CONNECTED)?.text)
        assertFalse(LinkTransitions.isFreshConnection(RECONNECTING, CONNECTED))
    }

    @Test
    fun perdidaDeConexion_avisaComoError() {
        val notice = LinkTransitions.noticeFor(CONNECTED, RECONNECTING)!!
        assertTrue(notice.isError)
        assertTrue(notice.text.startsWith("Conexión perdida"))
    }

    @Test
    fun desconexion_segunDeDondeVenga() {
        assertNull("sin aviso en el estado inicial", LinkTransitions.noticeFor(null, DISCONNECTED))
        assertEquals("Desconectado.", LinkTransitions.noticeFor(CONNECTED, DISCONNECTED)?.text)
        assertTrue(LinkTransitions.noticeFor(RECONNECTING, DISCONNECTED)!!.text.startsWith("No se pudo reconectar"))
        assertNull(LinkTransitions.noticeFor(DISCONNECTED, DISCONNECTED))
    }
}
