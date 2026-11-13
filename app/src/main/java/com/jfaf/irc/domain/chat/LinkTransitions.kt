package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.text.Msg

/** Estado de la conexión con el servidor tal como lo ve la UI. */
enum class LinkState {
    CONNECTED, RECONNECTING, DISCONNECTED;

    companion object {
        fun of(connected: Boolean, reconnecting: Boolean): LinkState = when {
            connected -> CONNECTED
            reconnecting -> RECONNECTING
            else -> DISCONNECTED
        }
    }
}

/** Aviso de sistema que se muestra en la pestaña del servidor al cambiar el estado. */
data class LinkNotice(val msg: Msg, val isError: Boolean = false)

/**
 * Decide qué aviso mostrar según la transición de estado (anterior -> nuevo).
 * Se basa únicamente en los estados, nunca en el texto de mensajes ya mostrados.
 */
object LinkTransitions {

    /** `previous == null` significa "primer estado observado"; no se avisa de una desconexión inicial. */
    fun noticeFor(previous: LinkState?, new: LinkState): LinkNotice? = when (new) {
        LinkState.CONNECTED ->
            if (previous == LinkState.RECONNECTING) LinkNotice(Msg.LINK_RECONNECTED) else LinkNotice(Msg.LINK_CONNECTED)

        LinkState.RECONNECTING -> LinkNotice(Msg.LINK_LOST_RECONNECTING, isError = true)

        LinkState.DISCONNECTED -> when (previous) {
            null -> null
            LinkState.RECONNECTING -> LinkNotice(Msg.LINK_RECONNECT_FAILED, isError = true)
            LinkState.CONNECTED -> LinkNotice(Msg.LINK_DISCONNECTED, isError = true)
            LinkState.DISCONNECTED -> null
        }
    }

    /** Una conexión nueva (no una reconexión) debe reiniciar pestañas y mensajes. */
    fun isFreshConnection(previous: LinkState?, new: LinkState): Boolean =
        new == LinkState.CONNECTED && previous != LinkState.RECONNECTING
}
