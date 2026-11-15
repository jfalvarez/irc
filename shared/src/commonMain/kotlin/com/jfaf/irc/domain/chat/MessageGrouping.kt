package com.jfaf.irc.domain.chat

import androidx.compose.ui.text.AnnotatedString
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType

/**
 * Separa el nick y el cuerpo de un mensaje de conversación (UI-10). Su texto empieza por `<nick> `.
 * Qué mensajes llevan cabecera (avatar y nick) lo decide `ChatTimeline`.
 */
object MessageGrouping {

    /** Partes de un mensaje de conversación: quién habla y lo que dice (con los estilos mIRC). */
    data class Speech(val nick: String, val body: AnnotatedString)

    private val SPEECH_TYPES = setOf(
        UiMessageType.CHANNEL_MSG_RECEIVED,
        UiMessageType.CHANNEL_MSG_SENT,
        UiMessageType.PRIVATE_MSG_RECEIVED,
        UiMessageType.PRIVATE_MSG_SENT
    )

    fun isSpeech(message: UiChatMessage): Boolean = message.type in SPEECH_TYPES

    /** Separa `<nick> texto`; null si no es un mensaje de conversación o no tiene ese formato. */
    fun speech(message: UiChatMessage): Speech? {
        if (!isSpeech(message)) return null
        val text = message.annotatedString ?: AnnotatedString(message.fullText)
        val raw = text.text
        if (!raw.startsWith("<")) return null
        val close = raw.indexOf("> ")
        if (close <= 1) return null
        val nick = raw.substring(1, close)
        if (nick.any { it.isWhitespace() }) return null
        return Speech(nick, text.subSequence(close + 2, raw.length))
    }
}
