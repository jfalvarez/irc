package com.jfaf.irc.domain.chat.model

import androidx.compose.ui.text.AnnotatedString

enum class MediaTypeEnum {
    IMAGE,
    VIDEO,
    NONE
}

/** Entrada, salida o `QUIT` de un usuario: los mensajes de este tipo se pueden agrupar en una sola línea. */
enum class PresenceEvent { JOIN, PART, QUIT }

data class UiChatMessage(
    val fullText: String,
    val annotatedString: AnnotatedString? = null,
    val type: UiMessageType,
    val sender: String? = null,
    val isOwnMessage: Boolean = false,
    val mediaUrl: String? = null, // Replaced imageUrl
    val mediaType: MediaTypeEnum = MediaTypeEnum.NONE, // Added mediaType
    /** Cuándo llegó (milisegundos de época); null si no se sabe (historial anterior a que se guardara). Sólo sirve a los separadores de fecha; no se muestra en cada mensaje. */
    val timestamp: Long? = null,
    /** Si es una entrada, salida o QUIT: permite agruparlos (`JOIN_PART_QUIT`). */
    val presence: PresenceEvent? = null
)

enum class UiMessageType {
    CHANNEL_MSG_RECEIVED,
    CHANNEL_MSG_SENT,
    PRIVATE_MSG_RECEIVED,
    PRIVATE_MSG_SENT,
    ACTION_MSG, // Para /me
    JOIN_PART_QUIT,
    NICK_CHANGE,
    MODE_CHANGE,
    NOTICE,
    SERVER_INFO,
    SYSTEM_MESSAGE,
    OTHER_COMMAND,
    AD_MESSAGE
}
