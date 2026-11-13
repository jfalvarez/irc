package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.domain.IrcConnection
import com.jfaf.irc.domain.chat.ChatStateManager
import javax.inject.Inject

sealed interface PartChannelResult {
    object Success : PartChannelResult
    data class Failure(val message: String) : PartChannelResult
}

class PartChannelUseCase @Inject constructor(
    private val strings: StringProvider,
    private val ircConnection: IrcConnection,
    private val chatStateManager: ChatStateManager
) {
    suspend operator fun invoke(
        channelNameToPart: String?,
        currentActiveTarget: String?,
        partMessage: String?
    ): PartChannelResult {
        val targetToPart = channelNameToPart ?: currentActiveTarget
        val targetForErrorMessage = currentActiveTarget ?: ChatStateManager.SERVER_TARGET_ID

        if (targetToPart == null || !targetToPart.startsWith("#")) {
            val errorMessage = strings.get(Msg.PART_INVALID)
            chatStateManager.addSystemMessageToTarget(
                targetForErrorMessage,
                errorMessage, 
                isError = true
            )
            return PartChannelResult.Failure(errorMessage)
        }

        ircConnection.partChannel(targetToPart, partMessage)
        // Optionally, add a system message here confirming the part attempt, e.g.:
        // chatStateManager.addSystemMessageToTarget(targetToPart, "Saliendo de $targetToPart...")
        // However, the server usually sends PART messages that IrcMessageHandler will process.
        return PartChannelResult.Success
    }
}
