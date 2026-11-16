package com.jfaf.irc.domain.notifications

/** Avisos al usuario cuando no está mirando la app. Android: notificaciones del sistema. */
interface Notifier {
    /** Privado recibido. [conversationKey] agrupa por remitente: uno nuevo de otra persona no pisa el anterior. */
    fun showPrivateMessage(sender: String, text: String, conversationKey: String)
    fun showFriendOnline(nick: String)

    /**
     * Mensaje de un canal (UI-32): [isMention] si nombra al usuario. [channel] agrupa por canal. Por defecto no hace nada,
     * para las plataformas que aún no lo muestren.
     */
    fun showChannelMessage(channel: String, sender: String, text: String, isMention: Boolean) {}
}
