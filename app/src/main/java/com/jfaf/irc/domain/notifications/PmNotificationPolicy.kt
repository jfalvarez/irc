package com.jfaf.irc.domain.notifications

import com.jfaf.irc.data.model.ParsedIrcMessage

/** Reglas puras de cuándo y cómo se notifica un mensaje privado desde el servicio. */
object PmNotificationPolicy {

    /** Rango de ids de notificación reservado a conversaciones (los ids 1..3 son del servicio). */
    private const val ID_BASE = 1000
    private const val ID_RANGE = 10_000

    /** PRIVMSG dirigido a un usuario (no a un canal). */
    fun isPrivateMessage(message: ParsedIrcMessage): Boolean {
        val target = message.params.firstOrNull()
        return message.command == "PRIVMSG" && target != null && !target.startsWith("#")
    }

    /** true si hay que mostrar notificación: es un PM, la app está en segundo plano y el remitente no está ignorado. */
    fun shouldNotify(message: ParsedIrcMessage, ignoredUsers: Set<String>, appInForeground: Boolean): Boolean {
        if (appInForeground || !isPrivateMessage(message)) return false
        val sender = message.senderNickname ?: return true
        return ignoredUsers.none { it.equals(sender, ignoreCase = true) }
    }

    /** Clave de la conversación (el remitente, sin distinguir mayúsculas); agrupa las notificaciones por persona. */
    fun conversationKey(message: ParsedIrcMessage): String =
        (message.senderNickname ?: message.prefix ?: "server").lowercase()

    /** Id estable por conversación: un PM nuevo de otra persona no pisa la notificación anterior. */
    fun notificationId(conversationKey: String): Int =
        ID_BASE + conversationKey.hashCode().mod(ID_RANGE) // Kotlin común; mismo resultado que floorMod
}
