package com.jfaf.irc.ui.viewmodels

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
data class LinkNotice(val text: String, val isError: Boolean = false)

/**
 * Decide qué aviso mostrar según la transición de estado (anterior -> nuevo).
 * Se basa únicamente en los estados, nunca en el texto de mensajes ya mostrados.
 */
object LinkTransitions {

    /** `previous == null` significa "primer estado observado"; no se avisa de una desconexión inicial. */
    fun noticeFor(previous: LinkState?, new: LinkState): LinkNotice? = when (new) {
        LinkState.CONNECTED ->
            if (previous == LinkState.RECONNECTING) LinkNotice("Reconectado.") else LinkNotice("Conectado al servidor.")

        LinkState.RECONNECTING -> LinkNotice("Conexión perdida. Reconectando…", isError = true)

        LinkState.DISCONNECTED -> when (previous) {
            null -> null
            LinkState.RECONNECTING -> LinkNotice("No se pudo reconectar. Desconectado.", isError = true)
            LinkState.CONNECTED -> LinkNotice("Desconectado.", isError = true)
            LinkState.DISCONNECTED -> null
        }
    }

    /** Una conexión nueva (no una reconexión) debe reiniciar pestañas y mensajes. */
    fun isFreshConnection(previous: LinkState?, new: LinkState): Boolean =
        new == LinkState.CONNECTED && previous != LinkState.RECONNECTING
}
