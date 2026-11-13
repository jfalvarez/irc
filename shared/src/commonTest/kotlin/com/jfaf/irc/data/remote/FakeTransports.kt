package com.jfaf.irc.data.remote

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

/** Transporte en memoria que se puede reabrir (cada open() crea un canal nuevo del "servidor"). */
class ReopenableFakeTransport : IrcTransport {
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
