package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.IrcConnection

class UpdateFriendMonitoringUseCase(
    private val ircConnection: IrcConnection
) {
    suspend operator fun invoke(friends: Set<String>) {
        if (friends.isNotEmpty() && ircConnection.connectionState.value) {
            friends.forEach { friend ->
                ircConnection.sendRawCommand("WHOIS $friend", isSilent = true)
            }
        }
    }
}
