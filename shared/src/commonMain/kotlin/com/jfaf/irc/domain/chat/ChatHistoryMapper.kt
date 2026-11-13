package com.jfaf.irc.domain.chat

import com.jfaf.irc.data.history.StoredMessage
import com.jfaf.irc.domain.chat.model.MediaTypeEnum
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType

/** Conversión entre el modelo de UI y el modelo persistido. No se guardan los estilos (`annotatedString`). */
fun UiChatMessage.toStored(): StoredMessage =
    StoredMessage(fullText, type.name, sender, isOwnMessage, mediaUrl, mediaType.name)

/** Los estilos mIRC se reconstruyen desde el texto; el resto de estilos del mensaje original se pierde. */
fun StoredMessage.toUi(): UiChatMessage = UiChatMessage(
    fullText = fullText,
    annotatedString = MircColorParser.parse(fullText),
    type = UiMessageType.entries.firstOrNull { it.name == type } ?: UiMessageType.OTHER_COMMAND,
    sender = sender,
    isOwnMessage = isOwnMessage,
    mediaUrl = mediaUrl,
    mediaType = MediaTypeEnum.entries.firstOrNull { it.name == mediaType } ?: MediaTypeEnum.NONE
)

/** Mensajes que no deben guardarse (anuncios de relleno). */
fun UiChatMessage.isPersistable(): Boolean = type != UiMessageType.AD_MESSAGE
