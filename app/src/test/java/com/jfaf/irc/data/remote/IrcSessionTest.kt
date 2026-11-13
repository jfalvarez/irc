package com.jfaf.irc.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
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

/** Transporte en memoria que se puede reabrir (cada open() crea un canal nuevo del "servidor"). */
private class ReopenableFakeTransport : IrcTransport {
    @Volatile private var fromServer = Channel<String?>(Channel.UNLIMITED)
    val written = Channel<String>(Channel.UNLIMITED)
    val opened = Channel<Unit>(Channel.UNLIMITED)

    @Volatile override var isOpen = false
        private set

    override suspend fun open(host: String, port: Int, tls: Boolean) {
        fromServer = Channel(Channel.UNLIMITED)
        isOpen = true
        opened.send(Unit)
    }
    override suspend fun readLine(): String? = fromServer.receive()
    override suspend fun writeLine(line: String) { written.send(line) }
    override fun close() {
        isOpen = false
        fromServer.close()
    }

    /** Línea del servidor sobre la conexión abierta en este momento. */
    suspend fun serverSends(line: String?) = fromServer.send(line)

    /** Espera a la siguiente apertura y descarta el NICK/USER del registro. */
    suspend fun awaitOpenAndHandshake() {
        withTimeout(2_000) { opened.receive() }
        withTimeout(2_000) { written.receive() } // NICK
        withTimeout(2_000) { written.receive() } // USER
    }

    /** Siguiente línea escrita que empiece por [prefix] (ignora el resto). */
    suspend fun awaitWritten(prefix: String): String = withTimeout(2_000) {
        var line: String
        do { line = written.receive() } while (!line.startsWith(prefix))
        line
    }
}

class IrcSessionTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val policy = ReconnectPolicy(baseDelayMs = 10, maxDelayMs = 20, maxAttempts = 3, registerTimeoutMs = 300)
    private val transports = mutableMapOf<String, ReopenableFakeTransport>()

    private val session = IrcSession(scope, policy) { p, s, onLost ->
        val t = transports.getOrPut(p.host) { ReopenableFakeTransport() }
        ManualIrcClient(p.host, p.port, p.useSsl, s, Dispatchers.IO, onLost, t)
    }

    @After
    fun tearDown() = scope.cancel()

    private fun params(host: String = "irc.test") = ConnectParams("yo", host, 6667, false)

    private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T = withTimeout(2_000) { first(predicate) }

    @Test
    fun conectar_publicaConectado() = runBlocking {
        session.connect(params())
        transports.getValue("irc.test").awaitOpenAndHandshake()
        assertEquals(SessionStatus.Connected("irc.test"), session.status.await { it is SessionStatus.Connected })
        assertTrue(session.connectionState.value)
    }

    @Test
    fun perdida_reconectaYVuelveAConectado() = runBlocking {
        session.connect(params())
        val t = transports.getValue("irc.test")
        t.awaitOpenAndHandshake()
        t.serverSends(":server 001 yo :Welcome")
        session.status.await { it is SessionStatus.Connected }

        t.serverSends(null) // el servidor cierra
        assertEquals(SessionStatus.Reconnecting(1, 3), session.status.await { it is SessionStatus.Reconnecting })
        assertTrue(session.isReconnecting.value)

        t.awaitOpenAndHandshake()
        t.serverSends(":server 001 yo :Welcome back")
        session.status.await { it is SessionStatus.Connected }
        session.isReconnecting.await { !it }
        assertTrue(session.connectionState.value)
    }

    @Test
    fun sinRegistro_abandonaTrasMaxIntentos() = runBlocking {
        session.connect(params())
        val t = transports.getValue("irc.test")
        t.awaitOpenAndHandshake()
        t.serverSends(null) // se pierde y el servidor nunca vuelve a enviar el 001
        session.status.await { it == SessionStatus.Reconnecting(3, 3) }
        session.isReconnecting.await { !it }
        assertEquals(SessionStatus.Disconnected, session.status.value)
        assertFalse(session.connectionState.value)
    }

    @Test
    fun reintentoFallido_conservaLosCanales() = runBlocking {
        session.connect(params())
        val t = transports.getValue("irc.test")
        t.awaitOpenAndHandshake()
        t.serverSends(":server 001 yo :Welcome")
        session.status.await { it is SessionStatus.Connected }
        session.joinChannel("#c", null)
        assertEquals("JOIN #c", t.awaitWritten("JOIN"))

        t.serverSends(null)              // se pierde la conexión
        t.awaitOpenAndHandshake()        // intento 1: el servidor no envía el 001 → se aborta
        t.awaitOpenAndHandshake()        // intento 2
        t.serverSends(":server 001 yo :Welcome back")
        assertEquals("JOIN #c", t.awaitWritten("JOIN")) // vuelve a entrar en el canal
    }

    @Test
    fun desconectar_enviaQuitYNoReconecta() = runBlocking {
        session.connect(params())
        val t = transports.getValue("irc.test")
        t.awaitOpenAndHandshake()
        session.status.await { it is SessionStatus.Connected }

        session.disconnect("adios")
        assertEquals("QUIT :adios", t.awaitWritten("QUIT"))
        assertEquals(SessionStatus.Disconnected, session.status.value)
        assertFalse(session.connectionState.value)
        delay(100) // margen para que, si hubiera reconexión, arrancase
        assertFalse(session.isReconnecting.value)
        assertNull(withTimeoutOrNull(100) { t.opened.receive() })
    }

    @Test
    fun reconectarConOtroCliente_cierraSoloElViejo() = runBlocking {
        session.connect(params("viejo.test"))
        val old = transports.getValue("viejo.test")
        old.awaitOpenAndHandshake()
        old.serverSends(":server 001 yo :Welcome")
        session.status.await { it == SessionStatus.Connected("viejo.test") }

        // Simula un cliente que ya no está "conectado" para forzar la creación de uno nuevo
        old.serverSends(null)
        session.status.await { it is SessionStatus.Reconnecting }
        session.connect(params("nuevo.test"))
        val new = transports.getValue("nuevo.test")
        new.awaitOpenAndHandshake()

        session.status.await { it == SessionStatus.Connected("nuevo.test") }
        delay(100) // deja correr la desconexión del cliente viejo
        assertFalse(old.isOpen)
        assertTrue(new.isOpen) // la desconexión del viejo no afecta al nuevo
    }
}
