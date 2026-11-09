package com.jfaf.irc.ui.model

import androidx.compose.ui.text.AnnotatedString

enum class MediaTypeEnum {
    IMAGE,
    VIDEO,
    NONE
}

data class UiChatMessage(
    val fullText: String,
    val annotatedString: AnnotatedString? = null,
    val type: UiMessageType,
    val sender: String? = null,
    val isOwnMessage: Boolean = false,
    val mediaUrl: String? = null, // Replaced imageUrl
    val mediaType: MediaTypeEnum = MediaTypeEnum.NONE // Added mediaType
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
