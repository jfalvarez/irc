package com.jfaf.irc.data.remote

import com.jfaf.irc.data.model.NickFallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/** Transporte en memoria: el test "envía" líneas del servidor y lee lo que escribe el cliente. */
private class FakeIrcTransport : IrcTransport {
    val fromServer = Channel<String?>(Channel.UNLIMITED)
    val written = Channel<String>(Channel.UNLIMITED)

    @Volatile override var isOpen = false
        private set

    override suspend fun open(host: String, port: Int, tls: Boolean) { isOpen = true }
    override suspend fun readLine(): String? = fromServer.receive()
    override suspend fun writeLine(line: String) { written.send(line) }
    override fun close() {
        isOpen = false
        fromServer.close()
    }
}

class ManualIrcClientTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val transport = FakeIrcTransport()
    private val connectionLost = AtomicBoolean(false)
    private val client = ManualIrcClient(
        host = "irc.test", port = 6667, useSsl = false,
        coroutineScope = scope,
        onConnectionLost = { connectionLost.set(true) },
        transport = transport
    )

    @After
    fun tearDown() = scope.cancel()

    private suspend fun nextWritten(): String = withTimeout(2_000) { transport.written.receive() }

    /** Conecta y consume el NICK/USER iniciales. */
    private suspend fun connected(nick: String = "yo") {
        client.connect(nick)
        nextWritten()
        nextWritten()
    }

    @Test
    fun alConectar_enviaNickYUser() = runBlocking {
        client.connect("yo")
        assertEquals("NICK yo", nextWritten())
        assertEquals("USER yo 0 * :yo", nextWritten())
    }

    @Test
    fun ping_respondePong() = runBlocking {
        connected()
        transport.fromServer.send("PING :abc")
        assertEquals("PONG :abc", nextWritten())
    }

    @Test
    fun nickEnUso_pruebaAlternativo() = runBlocking {
        connected()
        transport.fromServer.send(":server 433 * yo :Nickname is already in use")
        assertEquals("NICK ${NickFallback.alternative("yo", 1)}", nextWritten())
    }

    @Test
    fun joinAntesDel001_seEnviaAlRegistrarse() = runBlocking {
        connected()
        client.joinChannel("#c")
        assertNull(withTimeoutOrNull(200) { transport.written.receive() })
        transport.fromServer.send(":server 001 yo :Welcome")
        assertEquals("JOIN #c", nextWritten())
    }

    @Test
    fun servidorCierra_avisaPerdidaDeConexion() = runBlocking {
        connected()
        transport.fromServer.send(null)
        withTimeout(2_000) { client.connectionState.first { !it } }
        withTimeout(2_000) { while (!connectionLost.get()) kotlinx.coroutines.delay(10) }
        assertTrue(connectionLost.get())
    }

    @Test
    fun desconectar_enviaQuitAntesDeCerrar() = runBlocking {
        connected()
        client.disconnectAndCleanup("adios")
        assertEquals("QUIT :adios", nextWritten())
        assertFalse(transport.isOpen)
        assertFalse(client.connectionState.value)
        assertFalse(connectionLost.get()) // cierre pedido por el usuario: no es una pérdida
    }
}
