package com.jfaf.irc.domain.usecase

import android.util.Log
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import javax.inject.Inject

sealed interface JoinChannelResult {
    object Success : JoinChannelResult
    data class Failure(val message: String, val isError: Boolean = true) : JoinChannelResult
}

class JoinChannelUseCase @Inject constructor(
    private val ircRepository: IrcRepository,
    private val chatStateManager: ChatStateManager
) {
    suspend operator fun invoke(channelName: String, key: String?): JoinChannelResult {
        val currentActiveTargetForMsg = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID

        if (!channelName.startsWith("#")) {
            val errorMsg = "Nombre de canal inválido: $channelName. Debe empezar con #."
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForMsg, errorMsg, isError = true)
            return JoinChannelResult.Failure(errorMsg)
        }

        if (ircRepository.connectionState.value && channelName.isNotBlank()) {
            // Message about attempting to join is good UX, can be kept here or in ViewModel.
            // For consistency with how other messages are handled, let's keep it in the UseCase for now.
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForMsg, "Solicitando unirse a $channelName...")
            ircRepository.joinChannel(channelName, key)
            return JoinChannelResult.Success
        } else {
            val errorMsg = "No se puede unir al canal. No conectado."
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForMsg, errorMsg, isError = true)
            return JoinChannelResult.Failure(errorMsg)
        }
    }
}
