package com.jfaf.irc.domain.usecase

import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.domain.channel.ChannelListCollector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestChannelListUseCaseTest {

    private val collector = ChannelListCollector()
    private val line = ParsedIrcMessage(rawLine = "", command = "322", params = listOf("yo", "#a", "3"), trailing = "[+nt] x")

    @Test
    fun conectado_enviaListYAnunciaLaConsulta() {
        val irc = FakeIrcConnection()
        assertEquals(ChannelListRequestResult.Success, RequestChannelListUseCase(irc, collector)())
        assertEquals(listOf("raw LIST"), irc.sent)
        assertTrue(collector.accept(line))
    }

    @Test
    fun sinConexion_noEnviaNiAnuncia() {
        val irc = FakeIrcConnection(connected = false)
        assertEquals(ChannelListRequestResult.NotConnected, RequestChannelListUseCase(irc, collector)())
        assertTrue(irc.sent.isEmpty())
        assertFalse(collector.accept(line))
    }
}
