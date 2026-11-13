package com.jfaf.irc.data.repositories

import com.jfaf.irc.data.remote.IrcSession
import com.jfaf.irc.data.remote.ManualIrcClient
import com.jfaf.irc.data.remote.ReconnectPolicy
import com.jfaf.irc.data.remote.ReopenableFakeTransport
import com.jfaf.irc.data.remote.SessionStatus
import com.jfaf.irc.domain.platform.ConnectionHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.Test
import java.util.Collections

class IrcRepositoryTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val transport = ReopenableFakeTransport()
    private val session = IrcSession(scope, ReconnectPolicy(baseDelayMs = 10, maxAttempts = 1)) { p, s, onLost ->
        ManualIrcClient(p.host, p.port, p.useSsl, s, Dispatchers.IO, onLost, transport)
    }

    /** Registra las llamadas al host junto con lo que ve la sesión en ese momento. */
    private val hostCalls = Collections.synchronizedList(mutableListOf<String>())
    private val host = object : ConnectionHost {
        override fun start() { hostCalls += "start (status=${session.status.value})" }
        override fun stop() { hostCalls += "stop (conectado=${session.connectionState.value})" }
    }

    private val repo = IrcRepository(session, host, scope)

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun conectar_arrancaElHostYConecta() = runBlocking {
        repo.connect("irc.test", 6667, false, "yo")
        transport.awaitOpenAndHandshake()
        withTimeout(2_000) { session.status.first { it == SessionStatus.Connected("irc.test") } }
        // El host se arranca antes de conectar (la sesión aún estaba desconectada)
        assertEquals(listOf("start (status=Disconnected)"), hostCalls.toList())
    }

    @Test
    fun desconectar_enviaQuitYParaElHost() = runBlocking {
        repo.connect("irc.test", 6667, false, "yo")
        transport.awaitOpenAndHandshake()
        withTimeout(2_000) { repo.connectionState.first { it } }

        repo.disconnect("adios")
        assertEquals("QUIT :adios", transport.awaitWritten("QUIT"))
        withTimeout(2_000) { while (hostCalls.size < 2) delay(10) }
        // Se para el host cuando la sesión ya está desconectada
        assertEquals("stop (conectado=false)", hostCalls[1])
    }

    @Test
    fun mensajes_seRecuperanAlSuscribirseTarde() = runBlocking {
        repo.connect("irc.test", 6667, false, "yo")
        transport.awaitOpenAndHandshake()
        transport.serverSends(":ana!u@h PRIVMSG yo :hola")
        // Nadie escucha todavía: el mensaje queda en el búfer de 50
        withTimeout(2_000) { while (repo.incomingMessages.replayCache.isEmpty()) delay(10) }

        val first = withTimeout(2_000) { repo.incomingMessages.first() }
        assertEquals("PRIVMSG", first.command)
        assertEquals("hola", first.trailing)
    }
}
