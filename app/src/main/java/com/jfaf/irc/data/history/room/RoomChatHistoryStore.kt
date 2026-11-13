package com.jfaf.irc.data.history.room

import com.jfaf.irc.data.history.ChatHistoryStore
import com.jfaf.irc.data.history.ChatSnapshot
import com.jfaf.irc.data.history.StoredMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomChatHistoryStore @Inject constructor(
    private val dao: ChatHistoryDao
) : ChatHistoryStore {

    override suspend fun load(): ChatSnapshot {
        val tabs = dao.tabs()
        val messages = dao.messages()
            .groupBy({ it.target }, { it.toStored() })
        return ChatSnapshot(
            tabs = tabs.map { it.target },
            activeTarget = tabs.firstOrNull { it.isActive }?.target,
            messages = messages
        )
    }

    override suspend fun saveTabs(tabs: List<String>, activeTarget: String?) {
        dao.replaceTabs(tabs.mapIndexed { index, target -> TabEntity(target, index, target == activeTarget) })
    }

    override suspend fun replaceMessages(target: String, messages: List<StoredMessage>) {
        dao.replaceMessages(target, messages.mapIndexed { index, m -> m.toEntity(target, index) })
    }

    override suspend fun deleteTarget(target: String) {
        dao.deleteMessages(target)
    }

    private fun MessageEntity.toStored() = StoredMessage(fullText, type, sender, isOwnMessage, mediaUrl, mediaType)

    private fun StoredMessage.toEntity(target: String, position: Int) = MessageEntity(
        target = target, position = position, fullText = fullText, type = type, sender = sender,
        isOwnMessage = isOwnMessage, mediaUrl = mediaUrl, mediaType = mediaType
    )
}
