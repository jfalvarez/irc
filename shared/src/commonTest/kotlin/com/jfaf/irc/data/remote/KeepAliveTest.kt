package com.jfaf.irc.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Detección de conexiones muertas sin error: PING propio tras un rato sin tráfico (K5/#5). */
class KeepAliveTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val transport = ReopenableFakeTransport()
    private val lost = AtomicInteger(0)

    private fun client(idleMs: Long = 150, replyMs: Long = 150) = ManualIrcClient(
        host = "irc.test", port = 6667, useSsl = false,
        coroutineScope = scope,
        onConnectionLost = { lost.incrementAndGet() },
        transport = transport,
        keepAlive = KeepAlivePolicy(idleMs, replyMs)
    )

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun sinTrafico_enviaPingYSiNadieResponde_daLaConexionPorPerdida() = runBlocking {
        val client = client()
        client.connect("yo")
        transport.awaitOpenAndHandshake()
        assertEquals("PING :keepalive", transport.awaitWritten("PING"))
        withTimeout(2_000) { client.connectionState.first { !it } }
        withTimeout(2_000) { while (lost.get() == 0) delay(10) }
        assertFalse(transport.isOpen, "Se cierra el transporte (desbloquea el readLine)")
        assertTrue(lost.get() >= 1)
    }

    @Test
    fun siElServidorResponde_laConexionSeMantieneYSeVuelveAPinguear() = runBlocking {
        val client = client()
        client.connect("yo")
        transport.awaitOpenAndHandshake()
        repeat(3) {
            transport.awaitWritten("PING")
            transport.serverSends(":irc.test PONG irc.test :keepalive")
        }
        assertTrue(transport.isOpen)
        assertTrue(client.connectionState.value)
        assertEquals(0, lost.get())
    }

    @Test
    fun conTrafico_noSePinguea() = runBlocking {
        val client = client(idleMs = 400, replyMs = 400)
        client.connect("yo")
        transport.awaitOpenAndHandshake()
        repeat(10) { // una línea cada 100 ms durante 1 s: nunca pasan 400 ms en silencio
            transport.serverSends(":irc.test NOTICE * :hola")
            delay(100)
        }
        assertNull(withTimeoutOrNull(50) { transport.awaitWritten("PING") }, "Con tráfico no hace falta PING")
        assertTrue(client.connectionState.value)
        assertEquals(0, lost.get())
    }

    @Test
    fun alDesconectarElUsuario_noSeAvisaDePerdida() = runBlocking {
        val client = client()
        client.connect("yo")
        transport.awaitOpenAndHandshake()
        client.disconnectAndCleanup()
        delay(500) // más que idle + respuesta
        assertEquals(0, lost.get())
        assertNull(withTimeoutOrNull(50) { transport.awaitWritten("PING") })
    }
}
