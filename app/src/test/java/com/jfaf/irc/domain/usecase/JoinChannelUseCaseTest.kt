package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.chat.ChatStateManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinChannelUseCaseTest {

    private val chat = ChatStateManager()

    private fun useCase(irc: FakeIrcConnection) = JoinChannelUseCase(fakeStrings, irc, chat)

    private fun serverTexts() = chat.allMessages.value[ChatStateManager.SERVER_TARGET_ID].orEmpty().map { it.fullText }

    @Test
    fun nombreSinAlmohadilla_fallaYNoEnvia() = runBlocking {
        val irc = FakeIrcConnection()
        val r = useCase(irc)("canal", null)
        assertTrue(r is JoinChannelResult.Failure)
        assertTrue(irc.sent.isEmpty())
        assertTrue(serverTexts().any { it.startsWith("JOIN_INVALID_CHANNEL") })
    }

    @Test
    fun noConectado_fallaYNoEnvia() = runBlocking {
        val irc = FakeIrcConnection(connected = false)
        val r = useCase(irc)("#c", null)
        assertTrue(r is JoinChannelResult.Failure)
        assertTrue(irc.sent.isEmpty())
    }

    @Test
    fun conectado_entraEnElCanal() = runBlocking {
        val irc = FakeIrcConnection()
        val r = useCase(irc)("#c", "clave")
        assertEquals(JoinChannelResult.Success, r)
        assertEquals(listOf("join #c clave"), irc.sent)
    }
}
