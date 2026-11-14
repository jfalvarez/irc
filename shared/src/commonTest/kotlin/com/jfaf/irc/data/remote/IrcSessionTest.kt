package com.jfaf.irc.data.remote

import com.jfaf.irc.domain.platform.NetworkMonitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test


class IrcSessionTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val policy = ReconnectPolicy(baseDelayMs = 10, maxDelayMs = 20, maxAttempts = 3, registerTimeoutMs = 300)
    private val transports = mutableMapOf<String, ReopenableFakeTransport>()

    private val session = IrcSession(scope, policy) { p, s, onLost ->
        val t = transports.getOrPut(p.host) { ReopenableFakeTransport() }
        ManualIrcClient(p.host, p.port, p.useSsl, s, Dispatchers.IO, onLost, t)
    }

    @AfterTest
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

    /** Sesión con una red de pega que el test enciende y apaga. */
    private val network = object : NetworkMonitor {
        override val isOnline = MutableStateFlow(true)
    }
    private val sessionWithNetwork = IrcSession(scope, policy, network) { p, s, onLost ->
        val t = transports.getOrPut(p.host) { ReopenableFakeTransport() }
        ManualIrcClient(p.host, p.port, p.useSsl, s, Dispatchers.IO, onLost, t)
    }

    private suspend fun connectedWithNetwork(): ReopenableFakeTransport {
        sessionWithNetwork.connect(params())
        val t = transports.getValue("irc.test")
        t.awaitOpenAndHandshake()
        t.serverSends(":server 001 yo :Welcome")
        sessionWithNetwork.status.await { it is SessionStatus.Connected }
        return t
    }

    @Test
    fun sinRed_cortaEsperaSinGastarIntentosYReconectaAlVolver() = runBlocking {
        val t = connectedWithNetwork()

        network.isOnline.value = false
        assertEquals(SessionStatus.WaitingForNetwork, sessionWithNetwork.status.await { it == SessionStatus.WaitingForNetwork })
        assertTrue(sessionWithNetwork.isReconnecting.value)
        assertTrue(sessionWithNetwork.isWaitingForNetwork.value)
        withTimeout(2_000) { while (t.isOpen) delay(10) } // corta el socket al momento

        // Sin red no se intenta nada, por mucho más tiempo del que duran los 3 intentos
        delay(300)
        assertNull(withTimeoutOrNull(50) { t.opened.receive() })
        assertEquals(SessionStatus.WaitingForNetwork, sessionWithNetwork.status.value)

        network.isOnline.value = true
        t.awaitOpenAndHandshake()
        t.serverSends(":server 001 yo :Welcome back")
        sessionWithNetwork.status.await { it is SessionStatus.Connected }
        sessionWithNetwork.isReconnecting.await { !it }
        assertFalse(sessionWithNetwork.isWaitingForNetwork.value)
    }

    @Test
    fun desconectarMientrasSeEsperaLaRed_noReconectaAlVolver() = runBlocking {
        val t = connectedWithNetwork()
        network.isOnline.value = false
        sessionWithNetwork.status.await { it == SessionStatus.WaitingForNetwork }

        sessionWithNetwork.disconnect("adios")
        assertEquals(SessionStatus.Disconnected, sessionWithNetwork.status.value)
        assertFalse(sessionWithNetwork.isReconnecting.value)
        assertFalse(sessionWithNetwork.isWaitingForNetwork.value)

        network.isOnline.value = true
        assertNull(withTimeoutOrNull(300) { t.opened.receive() })
    }

    @Test
    fun sinRedYSinConexion_noHaceNada() = runBlocking {
        network.isOnline.value = false
        delay(100)
        assertEquals(SessionStatus.Disconnected, sessionWithNetwork.status.value)
        assertFalse(sessionWithNetwork.isReconnecting.value)
    }
}
