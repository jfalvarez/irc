package com.jfaf.irc.domain.usecase

import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import javax.inject.Inject

class DisconnectUseCase @Inject constructor(
    private val ircRepository: IrcRepository,
    private val chatStateManager: ChatStateManager
) {
    suspend operator fun invoke(quitMessage: String?) {
        // Determine target for system message (could be active or server tab)
        // This logic is similar to what was in ViewModel, if a more specific target is needed.
        // For simplicity, we can assume SERVER_TARGET_ID or let ViewModel decide if more context is needed.
        // Let's use the current active target or the first available target for the message, then server as fallback.
        val targetForMessage = chatStateManager.activeTarget.value 
            ?: chatStateManager.chatTargets.value.firstOrNull() 
            ?: ChatStateManager.SERVER_TARGET_ID

        chatStateManager.addSystemMessageToTarget(
            targetForMessage, 
            "Desconectando y solicitando detener el servicio..."
        )
        ircRepository.disconnectAndStopService(quitMessage)
    }
}
