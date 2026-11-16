package com.jfaf.irc.domain.chat

/** Qué muestra el banner de conexión sobre el chat (UI-28). */
enum class ConnectionBannerState { HIDDEN, RECONNECTING, WAITING_FOR_NETWORK, RESTORED }

/** Reglas puras del banner de conexión y de lo que se apaga mientras no hay enlace. */
object ConnectionBanner {

    /** Cuánto se queda el aviso «Conexión recuperada» tras una reconexión. */
    const val RESTORED_VISIBLE_MILLIS = 2500L

    /** Banner mientras dura la reconexión; [waitingForNetwork] distingue «sin Internet» de «reintentando». */
    fun of(reconnecting: Boolean, waitingForNetwork: Boolean): ConnectionBannerState = when {
        !reconnecting -> ConnectionBannerState.HIDDEN
        waitingForNetwork -> ConnectionBannerState.WAITING_FOR_NETWORK
        else -> ConnectionBannerState.RECONNECTING
    }

    /** Se acaba de recuperar la conexión: se estaba reconectando y ahora hay enlace (no si se rindió). */
    fun isRestored(wasReconnecting: Boolean, reconnecting: Boolean, connected: Boolean): Boolean =
        wasReconnecting && !reconnecting && connected

    /** El campo de mensajes sigue en pantalla durante la reconexión (conserva lo escrito) pero no deja enviar. */
    fun isInputEnabled(reconnecting: Boolean): Boolean = !reconnecting
}
