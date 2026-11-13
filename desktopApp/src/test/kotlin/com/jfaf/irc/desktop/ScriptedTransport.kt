package com.jfaf.irc.desktop

import com.jfaf.irc.data.remote.IrcTransport
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.Volatile

/** Servidor IRC de pega para las pruebas de UI: lo que se encola con [serverSays] lo "recibe" la app. */
class ScriptedTransport : IrcTransport {
    private val incoming = Channel<String?>(Channel.UNLIMITED)
    val sent = CopyOnWriteArrayList<String>()

    @Volatile
    private var open = false

    override val isOpen: Boolean get() = open

    override suspend fun open(host: String, port: Int, tls: Boolean) {
        open = true
    }

    override suspend fun readLine(): String? = incoming.receive()

    override suspend fun writeLine(line: String) {
        sent += line
    }

    override fun close() {
        open = false
        incoming.trySend(null)
    }

    fun serverSays(vararg lines: String) {
        lines.forEach { incoming.trySend(it) }
    }
}
