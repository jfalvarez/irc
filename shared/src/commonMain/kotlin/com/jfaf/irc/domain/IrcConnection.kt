package com.jfaf.irc.domain

import com.jfaf.irc.data.model.ParsedIrcMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Conexión IRC tal como la ve la lógica de la app. Android: [com.jfaf.irc.data.repositories.IrcRepository]. */
interface IrcConnection : IrcSender {
    val connectionState: StateFlow<Boolean>
    val isReconnecting: StateFlow<Boolean>
    /** Reconectando pero sin red: se volverá a intentar cuando vuelva. */
    val isWaitingForNetwork: StateFlow<Boolean>
    val incomingMessages: SharedFlow<ParsedIrcMessage>

    fun connect(serverHost: String, serverPort: Int, useSsl: Boolean, nickname: String)
    /** Envía QUIT, cierra la conexión y libera lo que la mantiene viva (en Android, el servicio). */
    fun disconnect(quitMessage: String? = null)
    fun joinChannel(channelName: String, key: String? = null)
    fun partChannel(channelName: String, partMessage: String? = null)

    /**
     * Responde a [target] desde fuera del chat (la respuesta directa de una notificación): envía cada
     * línea de [message] como un mensaje y la publica en [incomingMessages] como propia, porque el
     * servidor no devuelve lo que uno envía y el chat no la vería. false si no hay conexión o está vacío.
     */
    fun sendReply(target: String, message: String): Boolean

    /** WHOIS lanzados en silencio (comprobación de amigos): sus respuestas no se muestran. */
    fun isWhoisSilent(nick: String): Boolean
    fun completeSilentWhois(nick: String)
}
