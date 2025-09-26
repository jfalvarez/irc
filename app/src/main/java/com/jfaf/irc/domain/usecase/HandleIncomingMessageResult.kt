package com.jfaf.irc.domain.usecase

/**
 * Represents the outcome of processing an incoming IRC message for UI display.
 */
data class HandleIncomingMessageResult(
    /**
     * If the incoming message resulted in a change to the user's own nickname,
     * this will contain the new nickname. Otherwise, it's null.
     */
    val newCurrentNickname: String? = null,

    /**
     * If a private message event should be triggered for a specific nick (and it's not an ignored user),
     * this will contain the nick. Otherwise, it's null.
     */
    val privateMessageEventNick: String? = null,

    /**
     * Indicates if the message was processed and led to UI updates.
     * Can be false if the message was filtered out (e.g., a PING message when preferences hide them).
     */
    val messageProcessed: Boolean = true
)
