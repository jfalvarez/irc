package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.chat.ChatStateManager
import com.jfaf.irc.domain.platform.AnalyticsTracker
import com.jfaf.irc.domain.user.WhoisCollector
import com.jfaf.irc.data.model.ParsedIrcMessage
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class PerformWhoisUseCaseTest {

    private val chat = ChatStateManager()
    private val collector = WhoisCollector()
    private val events = mutableListOf<Pair<String, Map<String, String>>>()
    private val analytics = object : AnalyticsTracker {
        override fun logEvent(name: String, params: Map<String, String>) { events += name to params }
    }

    private fun useCase(irc: FakeIrcConnection) = PerformWhoisUseCase(fakeStrings, irc, chat, analytics, collector)

    private fun serverTexts() = chat.allMessages.value[ChatStateManager.SERVER_TARGET_ID].orEmpty().map { it.fullText }

    @Test
    fun nickVacio_esInvalido() = runBlocking {
        val irc = FakeIrcConnection()
        assertEquals(WhoisRequestResult.InvalidNickInput, useCase(irc)(" "))
        assertTrue(irc.sent.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun noConectado_avisaEnServidor() = runBlocking {
        val irc = FakeIrcConnection(connected = false)
        assertEquals(WhoisRequestResult.NotConnected, useCase(irc)("ana"))
        assertTrue(irc.sent.isEmpty())
        assertTrue(serverTexts().contains("WHOIS_NOT_CONNECTED"))
    }

    @Test
    fun conectado_enviaWhoisYRegistraEvento() = runBlocking {
        val irc = FakeIrcConnection()
        assertEquals(WhoisRequestResult.Success, useCase(irc)("ana"))
        assertEquals(listOf("raw WHOIS ana"), irc.sent)
        assertEquals(listOf("whois_request" to mapOf("whois_target_nick" to "ana")), events)
    }

    @Test
    fun conectado_anunciaElNickAlColector() = runBlocking {
        val irc = FakeIrcConnection()
        useCase(irc)("ana")
        // Con el WHOIS anunciado, el colector consume sus respuestas
        val reply = ParsedIrcMessage(rawLine = "", command = "311", params = listOf("yo", "ana", "u", "h", "*"), trailing = "Ana")
        assertTrue(collector.accept(reply))
    }
}
