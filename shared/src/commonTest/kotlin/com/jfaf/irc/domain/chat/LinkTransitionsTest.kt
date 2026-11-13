package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.chat.LinkState.CONNECTED
import com.jfaf.irc.domain.chat.LinkState.DISCONNECTED
import com.jfaf.irc.domain.chat.LinkState.RECONNECTING
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class LinkTransitionsTest {

    @Test
    fun of_dasPrioridadAConectado() {
        assertEquals(CONNECTED, LinkState.of(connected = true, reconnecting = true))
        assertEquals(RECONNECTING, LinkState.of(connected = false, reconnecting = true))
        assertEquals(DISCONNECTED, LinkState.of(connected = false, reconnecting = false))
    }

    @Test
    fun primeraConexion_avisaYReinicia() {
        assertEquals(Msg.LINK_CONNECTED, LinkTransitions.noticeFor(null, CONNECTED)?.msg)
        assertEquals(Msg.LINK_CONNECTED, LinkTransitions.noticeFor(DISCONNECTED, CONNECTED)?.msg)
        assertTrue(LinkTransitions.isFreshConnection(null, CONNECTED))
        assertTrue(LinkTransitions.isFreshConnection(DISCONNECTED, CONNECTED))
    }

    @Test
    fun reconexion_noReiniciaElEstado() {
        assertEquals(Msg.LINK_RECONNECTED, LinkTransitions.noticeFor(RECONNECTING, CONNECTED)?.msg)
        assertFalse(LinkTransitions.isFreshConnection(RECONNECTING, CONNECTED))
    }

    @Test
    fun perdidaDeConexion_avisaComoError() {
        val notice = LinkTransitions.noticeFor(CONNECTED, RECONNECTING)!!
        assertTrue(notice.isError)
        assertEquals(Msg.LINK_LOST_RECONNECTING, notice.msg)
    }

    @Test
    fun desconexion_segunDeDondeVenga() {
        assertNull(LinkTransitions.noticeFor(null, DISCONNECTED), "sin aviso en el estado inicial")
        assertEquals(Msg.LINK_DISCONNECTED, LinkTransitions.noticeFor(CONNECTED, DISCONNECTED)?.msg)
        assertEquals(Msg.LINK_RECONNECT_FAILED, LinkTransitions.noticeFor(RECONNECTING, DISCONNECTED)!!.msg)
        assertNull(LinkTransitions.noticeFor(DISCONNECTED, DISCONNECTED))
    }
}
