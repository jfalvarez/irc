package com.jfaf.irc.domain.user

import com.jfaf.irc.data.model.ParsedIrcMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Agrupa las respuestas numéricas de un WHOIS (311, 312, 313, 317, 319, 301, 330, 378, 671 y el
 * cierre 318) en un [WhoisResult].
 *
 * Sólo recoge los nicks anunciados con [expect] (los que pide la UI): un `/whois` escrito a mano
 * y los WHOIS silenciosos de los amigos no pasan por aquí y siguen como antes. Lo llama una sola
 * corrutina (la que procesa los mensajes entrantes), así que no sincroniza.
 */
class WhoisCollector {

    private class Entry(val nick: String) {
        var sawUserInfo = false
        var notFound = false
        var user: String? = null
        var host: String? = null
        var realName: String? = null
        var server: String? = null
        var serverInfo: String? = null
        var idleSeconds: Long? = null
        var signOn: Long? = null
        val channels = mutableListOf<String>()
        var account: String? = null
        var isOperator = false
        var isSecure = false
        var away: String? = null
        var actualHost: String? = null

        fun toResult() = WhoisResult(
            nick = nick,
            found = sawUserInfo && !notFound,
            user = user, host = host, realName = realName,
            server = server, serverInfo = serverInfo,
            idleSeconds = idleSeconds, signOnEpochSeconds = signOn,
            channels = channels.toList(), account = account,
            isOperator = isOperator, isSecure = isSecure,
            awayMessage = away, actualHost = actualHost
        )
    }

    private val pending = mutableMapOf<String, Entry>()
    private val _results = MutableSharedFlow<WhoisResult>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Un resultado por cada WHOIS anunciado, cuando llega el cierre (318). */
    val results: SharedFlow<WhoisResult> = _results.asSharedFlow()

    /** Empieza a recoger las respuestas de [nick] (reinicia una consulta anterior del mismo nick). */
    fun expect(nick: String) {
        pending[key(nick)] = Entry(nick)
    }

    /** Deja de recoger [nick] (la UI descartó la consulta): sus líneas vuelven a la pestaña Servidor. */
    fun cancel(nick: String) {
        pending.remove(key(nick))
    }

    /**
     * Procesa [message]. Devuelve true si es una respuesta de un WHOIS anunciado y ya está
     * consumida (no debe pintarse en el chat). El 401 se anota pero sigue su camino, para que
     * quede en el Servidor el aviso de «usuario no encontrado».
     */
    fun accept(message: ParsedIrcMessage): Boolean {
        if (message.command !in COMMANDS) return false
        val nick = message.params.getOrNull(1) ?: return false
        val entry = pending[key(nick)] ?: return false
        val p = message.params
        val trailing = message.trailing

        when (message.command) {
            "311" -> {
                entry.sawUserInfo = true
                entry.user = p.getOrNull(2)
                entry.host = p.getOrNull(3)
                entry.realName = trailing
            }
            "312" -> {
                entry.server = p.getOrNull(2)
                entry.serverInfo = trailing
            }
            "313" -> entry.isOperator = true
            "317" -> {
                entry.idleSeconds = p.getOrNull(2)?.toLongOrNull()
                entry.signOn = p.getOrNull(3)?.toLongOrNull()
            }
            "319" -> entry.channels += trailing.orEmpty().split(' ').filter { it.isNotBlank() }
            "301" -> entry.away = trailing
            "330" -> entry.account = p.getOrNull(2)
            "378" -> entry.actualHost = trailing
            "671" -> entry.isSecure = true
            "401" -> {
                entry.notFound = true
                return false
            }
            "318" -> {
                pending.remove(key(nick))
                _results.tryEmit(entry.toResult())
            }
        }
        return true
    }

    private fun key(nick: String) = nick.lowercase()

    private companion object {
        val COMMANDS = setOf("311", "312", "313", "317", "318", "319", "301", "330", "378", "401", "671")
    }
}
