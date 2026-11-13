package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.domain.chat.ChatStateManager

class CloseTargetUseCase(
    private val strings: StringProvider,
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
                strings.get(Msg.CLOSE_SERVER_TAB_FORBIDDEN, ChatStateManager.SERVER_TARGET_ID), 
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
