package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.chat.model.PresenceEvent
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import kotlin.test.Test
import kotlin.test.assertEquals

class PresenceSummaryTest {
    private fun p(nick: String?, event: PresenceEvent?) =
        UiChatMessage(fullText = "x", type = UiMessageType.JOIN_PART_QUIT, sender = nick, presence = event)

    @Test
    fun separaEntradasDeSalidas() {
        val s = PresenceSummary.of(
            listOf(p("ana", PresenceEvent.JOIN), p("kiko", PresenceEvent.PART), p("luis", PresenceEvent.QUIT), p("eva", PresenceEvent.JOIN))
        )
        assertEquals(listOf("ana", "eva"), s.joined)
        assertEquals(listOf("kiko", "luis"), s.left)
    }

    @Test
    fun noRepiteNicksSinDistinguirMayusculas() {
        val s = PresenceSummary.of(listOf(p("Ana", PresenceEvent.JOIN), p("ana", PresenceEvent.JOIN), p("ANA", PresenceEvent.JOIN)))
        assertEquals(listOf("Ana"), s.joined)
    }

    @Test
    fun quienEntraYSaleApareceEnLasDosListas() {
        val s = PresenceSummary.of(listOf(p("ana", PresenceEvent.JOIN), p("ana", PresenceEvent.QUIT)))
        assertEquals(listOf("ana"), s.joined)
        assertEquals(listOf("ana"), s.left)
    }

    @Test
    fun ignoraLosSinNickOSinPresencia() {
        val s = PresenceSummary.of(listOf(p(null, PresenceEvent.JOIN), p("", PresenceEvent.QUIT), p("ana", null)))
        assertEquals(emptyList(), s.joined)
        assertEquals(emptyList(), s.left)
    }
}
