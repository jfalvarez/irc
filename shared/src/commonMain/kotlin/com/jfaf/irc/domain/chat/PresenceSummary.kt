package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.chat.model.PresenceEvent
import com.jfaf.irc.domain.chat.model.UiChatMessage

/** Quién entró y quién salió (salida o QUIT) en una tanda de avisos, sin repetir nicks y en orden de aparición (UI-6). */
data class PresenceSummary(val joined: List<String>, val left: List<String>) {
    companion object {
        fun of(messages: List<UiChatMessage>): PresenceSummary {
            fun nicks(match: (PresenceEvent) -> Boolean) = messages
                .filter { it.presence != null && match(it.presence) }
                .mapNotNull { it.sender?.takeIf(String::isNotBlank) }
                .distinctBy { it.lowercase() }
            return PresenceSummary(
                joined = nicks { it == PresenceEvent.JOIN },
                left = nicks { it == PresenceEvent.PART || it == PresenceEvent.QUIT }
            )
        }
    }
}
