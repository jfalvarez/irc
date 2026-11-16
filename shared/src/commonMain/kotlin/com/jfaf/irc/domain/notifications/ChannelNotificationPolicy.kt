package com.jfaf.irc.domain.notifications

import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.domain.chat.Mentions

/** Un mensaje de canal que hay que avisar: dónde, quién, qué dice y si nombra al usuario. */
data class ChannelAlert(val channel: String, val sender: String, val text: String, val isMention: Boolean)

/** Reglas puras de cuándo se avisa de un mensaje de canal con la app en segundo plano (UI-32). */
object ChannelNotificationPolicy {

    private const val CTCP_ACTION = "\u0001ACTION "

    /** PRIVMSG dirigido a un canal. */
    fun isChannelMessage(message: ParsedIrcMessage): Boolean {
        val target = message.params.firstOrNull()
        return message.command == "PRIVMSG" && target != null && target.startsWith("#")
    }

    /** El canal al que va el mensaje, tal como lo escribió el servidor. */
    fun channelOf(message: ParsedIrcMessage): String? = message.params.firstOrNull()?.takeIf { it.startsWith("#") }

    /**
     * El aviso que toca para [message] con el [level] del canal, o null si no hay que avisar: canal silenciado, remitente
     * desconocido, ignorado o el propio usuario, o (nivel «solo menciones») un mensaje que no nombra a [ownNick].
     */
    fun decide(
        message: ParsedIrcMessage,
        level: ChannelNotificationLevel,
        ownNick: String?,
        ignoredUsers: Set<String>
    ): ChannelAlert? {
        if (level == ChannelNotificationLevel.MUTED || !isChannelMessage(message)) return null
        val channel = channelOf(message) ?: return null
        val sender = message.senderNickname ?: return null
        if (ownNick != null && sender.equals(ownNick, ignoreCase = true)) return null
        if (ignoredUsers.any { it.equals(sender, ignoreCase = true) }) return null

        val text = displayText(message.trailing.orEmpty())
        val isMention = !ownNick.isNullOrBlank() && Mentions.isMention(text, ownNick)
        return if (level == ChannelNotificationLevel.ALL || isMention) ChannelAlert(channel, sender, text, isMention) else null
    }

    /** El texto como se ve: una acción (`/me`) llega envuelta en un código de control y se muestra como `* texto`. */
    fun displayText(trailing: String): String =
        if (trailing.startsWith(CTCP_ACTION)) "* " + trailing.removePrefix(CTCP_ACTION).removeSuffix("\u0001") else trailing
}
