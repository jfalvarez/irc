package com.jfaf.irc.data.repositories

import com.jfaf.irc.data.database.MessageDao
import com.jfaf.irc.data.database.MessageEntity
import com.jfaf.irc.ui.viewmodels.UiChatMessage
import com.jfaf.irc.ui.viewmodels.UiMessageType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageHistoryRepository @Inject constructor(
    private val messageDao: MessageDao
) {
    fun getHistoryForTarget(target: String): Flow<List<UiChatMessage>> {
        return messageDao.getMessagesForTarget(target).map {
            entities -> entities.map { it.toUiChatMessage() }
        }
    }
}

private fun MessageEntity.toUiChatMessage(): UiChatMessage {
    val fullText = if (isOwnMessage) "<You> $content" else "<${senderNick}> $content"
    return UiChatMessage(
        fullText = fullText,
        sender = senderNick,
        type = if (isOwnMessage) UiMessageType.PRIVATE_MSG_SENT else UiMessageType.PRIVATE_MSG_RECEIVED,
        isOwnMessage = isOwnMessage,
        timestamp = timestamp
        // Note: annotatedString is null, history won't have colors for now.
    )
}
