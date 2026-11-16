package com.jfaf.irc.domain.channel

import com.jfaf.irc.data.model.ParsedIrcMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Agrupa las respuestas de un `LIST` (321, 322 y el cierre 323) en un [ChannelListResult].
 *
 * Sólo recoge mientras haya una consulta anunciada con [expect] (la que pide la UI): un `/list`
 * en frío no se anuncia, y sus líneas siguen como antes. Mientras recoge, las líneas de la lista
 * se consumen para no inundar la pestaña Servidor (en una red grande son miles).
 *
 * Igual que [com.jfaf.irc.domain.user.WhoisCollector], no sincroniza: los mensajes entrantes y las
 * llamadas del ViewModel van todos por el hilo principal.
 */
class ChannelListCollector {

    private var entries: MutableList<ChannelListEntry>? = null
    private val _results = MutableSharedFlow<ChannelListResult>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Un resultado por cada consulta anunciada, cuando llega el cierre, el rechazo o se agota el tiempo. */
    val results: SharedFlow<ChannelListResult> = _results.asSharedFlow()

    /** Hay una consulta en curso. */
    val isCollecting: Boolean get() = entries != null

    /** Empieza a recoger (reinicia una consulta anterior). */
    fun expect() {
        entries = mutableListOf()
    }

    /** Abandona la consulta: lo que llegue después vuelve a la pestaña Servidor. */
    fun cancel() {
        entries = null
    }

    /** Se agotó el tiempo de espera: entrega lo recogido hasta ahora, marcado como incompleto. */
    fun timeOut() = finish(ChannelListStatus.TIMED_OUT)

    /**
     * Procesa [message]. Devuelve true si es parte de la consulta anunciada y ya está consumida
     * (no debe pintarse en el chat).
     */
    fun accept(message: ParsedIrcMessage): Boolean {
        val current = entries ?: return false
        when (message.command) {
            "321" -> Unit // RPL_LISTSTART: sólo la cabecera
            "322" -> parseEntry(message)?.let { current += it }
            "323" -> finish(ChannelListStatus.COMPLETE)
            // 263 RPL_TRYAGAIN y 416 ERR_TOOMANYMATCHES nombran el comando en el segundo parámetro
            "263", "416" -> {
                if (!message.params.getOrNull(1).equals("LIST", ignoreCase = true)) return false
                finish(ChannelListStatus.REFUSED)
            }
            else -> return false
        }
        return true
    }

    private fun finish(status: ChannelListStatus) {
        val collected = entries ?: return
        entries = null
        // Un canal sale una sola vez aunque el servidor lo repita (el nombre es la clave de la lista de la interfaz)
        _results.tryEmit(ChannelListResult(collected.distinctBy { it.name.lowercase() }, status))
    }

    private companion object {
        // «[+nt] tema»: los modos entre corchetes son opcionales (no todos los servidores los mandan)
        val MODES_PREFIX = Regex("""^\[\+?([^\]\s]*)\]\s?""")

        fun parseEntry(message: ParsedIrcMessage): ChannelListEntry? {
            // :servidor 322 yo #canal 12 :[+nt] tema
            val name = message.params.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
            val users = message.params.getOrNull(2)?.toIntOrNull() ?: 0
            val trailing = message.trailing.orEmpty()
            val modesMatch = MODES_PREFIX.find(trailing)
            return ChannelListEntry(
                name = name,
                users = users,
                modes = modesMatch?.groupValues?.get(1).orEmpty(),
                topic = if (modesMatch != null) trailing.substring(modesMatch.value.length) else trailing
            )
        }
    }
}
