package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.log.Log
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import javax.inject.Inject

sealed interface JoinChannelResult {
    object Success : JoinChannelResult
    data class Failure(val message: String, val isError: Boolean = true) : JoinChannelResult
}

class JoinChannelUseCase @Inject constructor(
    private val strings: StringProvider,
    private val ircRepository: IrcRepository,
    private val chatStateManager: ChatStateManager
) {
    suspend operator fun invoke(channelName: String, key: String?): JoinChannelResult {
        val currentActiveTargetForMsg = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID

        if (!channelName.startsWith("#")) {
            val errorMsg = strings.get(Msg.JOIN_INVALID_CHANNEL, channelName)
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForMsg, errorMsg, isError = true)
            return JoinChannelResult.Failure(errorMsg)
        }

        if (ircRepository.connectionState.value && channelName.isNotBlank()) {
            // Message about attempting to join is good UX, can be kept here or in ViewModel.
            // For consistency with how other messages are handled, let's keep it in the UseCase for now.
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForMsg, strings.get(Msg.JOIN_REQUESTING, channelName))
            ircRepository.joinChannel(channelName, key)
            return JoinChannelResult.Success
        } else {
            val errorMsg = strings.get(Msg.JOIN_NOT_CONNECTED)
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForMsg, errorMsg, isError = true)
            return JoinChannelResult.Failure(errorMsg)
        }
    }
}
