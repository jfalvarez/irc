package com.jfaf.irc.domain.usecase

import com.jfaf.irc.ui.viewmodels.ChatStateManager
import javax.inject.Inject

class CloseTargetUseCase @Inject constructor(
    private val chatStateManager: ChatStateManager,
    private val partChannelUseCase: PartChannelUseCase
) {
    suspend operator fun invoke(
        targetToClose: String,
        currentActiveTargetValue: String?,
        currentOwnNickname: String
    ) {
        val targetForSystemMessages = currentActiveTargetValue ?: ChatStateManager.SERVER_TARGET_ID

        if (targetToClose == ChatStateManager.SERVER_TARGET_ID) {
            chatStateManager.addSystemMessageToTarget(
                targetForSystemMessages, 
                "La pestaña '${ChatStateManager.SERVER_TARGET_ID}' no se puede cerrar.", 
                isError = true
            )
            return
        }
        
        if (targetToClose.startsWith("#")) {
            // PartChannelUseCase will handle its own system messages (including errors if any)
            partChannelUseCase(targetToClose, currentActiveTargetValue, null)
        } else {
            // This is for closing PM targets
            // ChatStateManager.closeTarget handles switching active target if needed
            chatStateManager.closeTarget(targetToClose, currentActiveTargetValue, currentOwnNickname)
            // It could also add a system message like "PM with $targetToClose closed" if desired,
            // but currently, it doesn't, to match existing behavior.
        }
    }
}
