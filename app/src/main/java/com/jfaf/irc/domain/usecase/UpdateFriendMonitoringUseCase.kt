package com.jfaf.irc.domain.usecase

import com.jfaf.irc.data.repositories.IrcRepository
import javax.inject.Inject

class UpdateFriendMonitoringUseCase @Inject constructor(
    private val ircRepository: IrcRepository
) {
    suspend operator fun invoke(friends: Set<String>) {
        if (friends.isNotEmpty() && ircRepository.connectionState.value) {
            friends.forEach { friend ->
                ircRepository.sendRawCommand("WHOIS $friend")
            }
        }
    }
}
