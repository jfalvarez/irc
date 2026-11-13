package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType

/** Preferencias que deciden qué mensajes se muestran en la lista del chat. */
data class MessageFilterOptions(
    val showJoinPartQuit: Boolean,
    val showNickChanges: Boolean,
    val showModeChanges: Boolean,
    val ignoredUsers: Set<String>
)

/** Lógica pura (sin Android) de filtrado e inserción de anuncios en la lista de mensajes. */
object UiMessageFilter {

    const val AD_POSITION = 5

    private val IGNORABLE_TYPES = setOf(
        UiMessageType.CHANNEL_MSG_RECEIVED,
        UiMessageType.PRIVATE_MSG_RECEIVED,
        UiMessageType.ACTION_MSG
    )

    private val JPQ_MARKERS = listOf("signed off", "connection closed")

    fun shouldDisplay(message: UiChatMessage, options: MessageFilterOptions): Boolean {
        val sender = message.sender
        if (sender != null && message.type in IGNORABLE_TYPES &&
            options.ignoredUsers.any { it.equals(sender, ignoreCase = true) }
        ) return false

        if (!options.showJoinPartQuit && (message.type == UiMessageType.JOIN_PART_QUIT || message.mentionsJpqMarker())) {
            return false
        }
        if (!options.showNickChanges && message.type == UiMessageType.NICK_CHANGE) return false
        if (!options.showModeChanges && message.type == UiMessageType.MODE_CHANGE) return false
        return true
    }

    /**
     * Inserta el anuncio en [AD_POSITION] (si hay anuncio y más de [AD_POSITION] mensajes)
     * y después aplica el filtro de preferencias.
     */
    fun apply(messages: List<UiChatMessage>, hasAd: Boolean, options: MessageFilterOptions): List<UiChatMessage> {
        val withAd = if (hasAd && messages.size > AD_POSITION) {
            messages.toMutableList().apply { add(AD_POSITION, UiChatMessage(fullText = "", type = UiMessageType.AD_MESSAGE)) }
        } else {
            messages
        }
        return withAd.filter { shouldDisplay(it, options) }
    }

    private fun UiChatMessage.mentionsJpqMarker(): Boolean =
        JPQ_MARKERS.any { fullText.contains(it, ignoreCase = true) || annotatedString?.text?.contains(it, ignoreCase = true) == true }
}
