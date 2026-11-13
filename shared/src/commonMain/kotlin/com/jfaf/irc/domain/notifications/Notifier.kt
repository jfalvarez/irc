package com.jfaf.irc.domain.notifications

/** Avisos al usuario cuando no está mirando la app. Android: notificaciones del sistema. */
interface Notifier {
    /** Privado recibido. [conversationKey] agrupa por remitente: uno nuevo de otra persona no pisa el anterior. */
    fun showPrivateMessage(sender: String, text: String, conversationKey: String)
    fun showFriendOnline(nick: String)
}
