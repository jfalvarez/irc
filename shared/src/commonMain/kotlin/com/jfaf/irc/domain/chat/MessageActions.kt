package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType

/** Lo que el menú de un mensaje pide meter en el campo de texto. */
sealed interface InputInsertion {
    /** Nombrar a [nick]: `nick: ` si el campo está vacío, `nick ` si ya hay texto. */
    data class Mention(val nick: String) : InputInsertion

    /** Citar [text]; [nick] (sólo en canales) se nombra delante si el campo está vacío. */
    data class Quote(val nick: String?, val text: String) : InputInsertion
}

/** Reglas puras del menú que se abre al mantener pulsado un mensaje: qué ofrece y qué texto produce. */
object MessageActions {

    const val MAX_QUOTE_LENGTH = 120

    /** El texto que se copia: el cuerpo en los mensajes de conversación (sin `<nick>`), el texto entero en los demás. */
    fun copyText(message: UiChatMessage): String =
        MessageGrouping.speech(message)?.body?.text
            ?: message.annotatedString?.text
            ?: message.fullText

    /** Todo menos los anuncios. */
    fun canCopy(message: UiChatMessage): Boolean =
        message.type != UiMessageType.AD_MESSAGE && copyText(message).isNotBlank()

    /** Sólo se menciona a quien habla en un canal, no a uno mismo. */
    fun canMention(message: UiChatMessage): Boolean =
        message.type == UiMessageType.CHANNEL_MSG_RECEIVED && MessageGrouping.speech(message) != null

    /** Se cita lo que dicen los demás, en canales y privados. */
    fun canQuote(message: UiChatMessage): Boolean =
        (message.type == UiMessageType.CHANNEL_MSG_RECEIVED || message.type == UiMessageType.PRIVATE_MSG_RECEIVED) &&
            MessageGrouping.speech(message) != null && copyText(message).isNotBlank()

    fun mentionInsertion(message: UiChatMessage): InputInsertion? =
        if (canMention(message)) MessageGrouping.speech(message)?.let { InputInsertion.Mention(it.nick) } else null

    fun quoteInsertion(message: UiChatMessage): InputInsertion? {
        if (!canQuote(message)) return null
        val nick = MessageGrouping.speech(message)?.nick
        return InputInsertion.Quote(
            nick = nick.takeIf { message.type == UiMessageType.CHANNEL_MSG_RECEIVED },
            text = copyText(message)
        )
    }

    /**
     * El texto a insertar en el cursor. [textBeforeCursor] es lo que ya hay antes del cursor y [isFieldEmpty] si el
     * campo entero está en blanco: sirve para decidir el `: ` inicial y si hace falta un espacio de separación.
     */
    fun render(insertion: InputInsertion, textBeforeCursor: String, isFieldEmpty: Boolean): String {
        val piece = when (insertion) {
            is InputInsertion.Mention -> if (isFieldEmpty) "${insertion.nick}: " else "${insertion.nick} "
            is InputInsertion.Quote -> {
                val quoted = "«${shorten(insertion.text)}» "
                if (isFieldEmpty && insertion.nick != null) "${insertion.nick}: $quoted" else quoted
            }
        }
        val needsSpace = textBeforeCursor.isNotEmpty() && !textBeforeCursor.last().isWhitespace()
        return if (needsSpace) " $piece" else piece
    }

    /** Una sola línea, sin espacios repetidos y con un máximo de [MAX_QUOTE_LENGTH] caracteres. */
    internal fun shorten(text: String): String {
        val oneLine = text.trim().split(Regex("[ \t\r\n]+")).joinToString(" ")
        return if (oneLine.length <= MAX_QUOTE_LENGTH) oneLine else oneLine.take(MAX_QUOTE_LENGTH - 1).trimEnd() + "…"
    }
}
