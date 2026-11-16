package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType

/**
 * Cuándo un canal o un privado se considera «sin mensajes todavía» (UI-37): cuando nadie ha dicho nada. Los avisos
 * del sistema que lo acompañan al abrirlo («Tester ha entrado a #test», «Chat privado con ana iniciado.») no cuentan,
 * salvo que sean muchos: entonces la lista ya está llena de cosas y la pista estorbaría.
 */
object EmptyChat {

    /** Con más líneas de sistema que éstas, la pestaña no se da por vacía aunque nadie haya hablado. */
    const val MAX_SYSTEM_LINES = 5

    private val conversation = setOf(
        UiMessageType.CHANNEL_MSG_RECEIVED,
        UiMessageType.CHANNEL_MSG_SENT,
        UiMessageType.PRIVATE_MSG_RECEIVED,
        UiMessageType.PRIVATE_MSG_SENT,
        UiMessageType.ACTION_MSG,
        UiMessageType.NOTICE
    )

    fun isEmpty(messages: List<UiChatMessage>): Boolean {
        var systemLines = 0
        for (message in messages) {
            when (message.type) {
                in conversation -> return false
                UiMessageType.AD_MESSAGE -> Unit
                else -> systemLines++
            }
        }
        return systemLines <= MAX_SYSTEM_LINES
    }
}
