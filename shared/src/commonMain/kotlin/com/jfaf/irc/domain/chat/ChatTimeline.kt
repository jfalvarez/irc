package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.chat.model.PresenceEvent
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType

/** Lo que pinta la lista del chat, en orden cronológico (UI-5, UI-6, UI-7, UI-10). */
sealed interface TimelineItem {
    /** Un mensaje. [showHeader]: lleva avatar y nick (primero de su tanda). [isMention]: nombra tu nick. */
    data class Message(val message: UiChatMessage, val showHeader: Boolean, val isMention: Boolean) : TimelineItem

    /** Dos o más entradas y salidas seguidas, plegables en una línea. */
    data class PresenceGroup(val messages: List<UiChatMessage>) : TimelineItem {
        val key: String get() = "${messages.first().timestamp}-${messages.first().fullText.hashCode()}"
    }

    /** Cambio de día: [epochDay] es el día local (días desde 1970-01-01). */
    data class DateSeparator(val epochDay: Long) : TimelineItem

    /** Desde aquí, lo que llegó mientras no mirabas la pestaña. */
    data object NewMessages : TimelineItem
}

/** Reglas puras que convierten la lista de mensajes de una pestaña en los elementos que se pintan. */
object ChatTimeline {

    /**
     * @param newFromEnd cuántos mensajes, contados desde el final, son nuevos (0 = ninguno): el separador va delante.
     * @param ownNick tu nick, para marcar las menciones; null o vacío = sin menciones.
     * @param dayOf día local de una hora (milisegundos de época); se inyecta para los tests.
     */
    fun build(
        messages: List<UiChatMessage>,
        newFromEnd: Int,
        ownNick: String?,
        dayOf: (Long) -> Long
    ): List<TimelineItem> {
        val items = mutableListOf<TimelineItem>()
        val newStart = if (newFromEnd in 1..messages.size) messages.size - newFromEnd else -1
        var lastDay: Long? = null
        var previousSpeaker: String? = null
        val run = mutableListOf<UiChatMessage>()

        fun flushRun() {
            if (run.size >= 2) {
                items += TimelineItem.PresenceGroup(run.toList())
            } else {
                run.forEach { items += TimelineItem.Message(it, showHeader = false, isMention = false) }
            }
            run.clear()
        }

        messages.forEachIndexed { index, message ->
            val day = message.timestamp?.let(dayOf)
            val dateBreak = day != null && day != lastDay
            val newBreak = index == newStart
            if (dateBreak || newBreak) {
                flushRun()
                previousSpeaker = null
            }
            if (dateBreak) {
                items += TimelineItem.DateSeparator(day!!)
                lastDay = day
            }
            if (newBreak) items += TimelineItem.NewMessages

            if (message.type == UiMessageType.JOIN_PART_QUIT && message.presence != null) {
                run += message
                previousSpeaker = null
                return@forEachIndexed
            }
            flushRun()

            val speech = MessageGrouping.speech(message)
            if (speech == null) {
                previousSpeaker = null
                items += TimelineItem.Message(message, showHeader = false, isMention = false)
            } else {
                val key = NickColors.normalize(speech.nick).lowercase()
                val header = key != previousSpeaker
                previousSpeaker = key
                val mention = message.type == UiMessageType.CHANNEL_MSG_RECEIVED &&
                    !ownNick.isNullOrBlank() && Mentions.isMention(speech.body.text, ownNick)
                items += TimelineItem.Message(message, showHeader = header, isMention = mention)
            }
        }
        flushRun()
        return items
    }
}
