package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.log.Log
import com.jfaf.irc.domain.IrcConnection
import com.jfaf.irc.domain.chat.ChatStateManager

sealed interface JoinChannelResult {
    object Success : JoinChannelResult
    data class Failure(val message: String, val isError: Boolean = true) : JoinChannelResult
}

class JoinChannelUseCase(
    private val strings: StringProvider,
    private val ircConnection: IrcConnection,
    private val chatStateManager: ChatStateManager
) {
    suspend operator fun invoke(channelName: String, key: String?): JoinChannelResult {
        val currentActiveTargetForMsg = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID

        if (!channelName.startsWith("#")) {
            val errorMsg = strings.get(Msg.JOIN_INVALID_CHANNEL, channelName)
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForMsg, errorMsg, isError = true)
            return JoinChannelResult.Failure(errorMsg)
        }

        if (ircConnection.connectionState.value && channelName.isNotBlank()) {
            // Message about attempting to join is good UX, can be kept here or in ViewModel.
            // For consistency with how other messages are handled, let's keep it in the UseCase for now.
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForMsg, strings.get(Msg.JOIN_REQUESTING, channelName))
            ircConnection.joinChannel(channelName, key)
            return JoinChannelResult.Success
        } else {
            val errorMsg = strings.get(Msg.JOIN_NOT_CONNECTED)
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForMsg, errorMsg, isError = true)
            return JoinChannelResult.Failure(errorMsg)
        }
    }
}
