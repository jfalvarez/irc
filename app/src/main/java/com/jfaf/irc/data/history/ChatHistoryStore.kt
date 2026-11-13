package com.jfaf.irc.data.history

/** Mensaje tal como se guarda (sólo tipos simples; sin Compose ni Android). */
data class StoredMessage(
    val fullText: String,
    val type: String,
    val sender: String?,
    val isOwnMessage: Boolean,
    val mediaUrl: String?,
    val mediaType: String
)

/** Pestañas abiertas, la activa y el historial reciente de cada una. */
data class ChatSnapshot(
    val tabs: List<String> = emptyList(),
    val activeTarget: String? = null,
    val messages: Map<String, List<StoredMessage>> = emptyMap()
)

/**
 * Persistencia del estado del chat. La lógica depende sólo de esta interfaz;
 * hoy la implementa Room (`RoomChatHistoryStore`), que también es multiplataforma.
 */
interface ChatHistoryStore {
    suspend fun load(): ChatSnapshot
    suspend fun saveTabs(tabs: List<String>, activeTarget: String?)
    suspend fun replaceMessages(target: String, messages: List<StoredMessage>)
    suspend fun deleteTarget(target: String)
}
