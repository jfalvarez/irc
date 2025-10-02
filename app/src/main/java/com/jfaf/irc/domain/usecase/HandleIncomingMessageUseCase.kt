package com.jfaf.irc.domain.usecase

import android.util.Log
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.repositories.UserMetadataRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import com.jfaf.irc.ui.viewmodels.ChatUiSnapshot
import com.jfaf.irc.ui.viewmodels.IrcMessageHandler
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class HandleIncomingMessageUseCase @Inject constructor(
    private val ircMessageHandler: IrcMessageHandler,
    private val chatStateManager: ChatStateManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userMetadataRepository: UserMetadataRepository
) {
    suspend operator fun invoke(
        parsedMessage: ParsedIrcMessage,
        currentOwnNickname: String // The VM still holds the most up-to-date version of this
    ): HandleIncomingMessageResult {

        val showPingPongMessages = userPreferencesRepository.showPingPongMessagesFlow.first()
        if (parsedMessage.command.equals("PING", ignoreCase = true) && !showPingPongMessages) {
            Log.d("HandleIncomingMsgUC", "PING message received and ignored for UI based on preference.")
            return HandleIncomingMessageResult(messageProcessed = false)
        }

        // Create a snapshot of the current UI state needed by the message handler
        val snapshot = ChatUiSnapshot(
            currentNickname = currentOwnNickname,
            activeTarget = chatStateManager.activeTarget.value,
            allMessages = chatStateManager.allMessages.value,
            chatTargets = chatStateManager.chatTargets.value,
            unreadTargets = chatStateManager.unreadTargets.value,
            usersInChannel = chatStateManager.usersInChannel.value
        )

        val handlerResult = ircMessageHandler.processMessage(snapshot, parsedMessage)

        // The ChatStateManager needs to know the current nickname *after* potential NICK changes
        // from the message, so we pass a lambda that can provide it. 
        // If handlerResult.newCurrentNickname is not null, that's the new one.
        // Otherwise, it's the one passed into the use case.
        val updatedNicknameProvider = { handlerResult.newCurrentNickname ?: currentOwnNickname }
        chatStateManager.updateStateFromHandlerResult(handlerResult, updatedNicknameProvider)

        val newNicknameForVm = handlerResult.newCurrentNickname
        var pmEventNickForVm: String? = null

        if (handlerResult.privateMessageEventNick != null) {
            val ignoredUsersLowercaseFromPrefs = userMetadataRepository.ignoredUsersFlow.first()
                .map { it.lowercase() }.toSet()
            if (handlerResult.privateMessageEventNick.lowercase() !in ignoredUsersLowercaseFromPrefs) {
                pmEventNickForVm = handlerResult.privateMessageEventNick
            } else {
                Log.d(
                    "HandleIncomingMsgUC", 
                    "PM Event for '${handlerResult.privateMessageEventNick}' from IrcMessageHandler suppressed as user is in ignored list: ${ignoredUsersLowercaseFromPrefs.joinToString()}"
                )
            }
        }

        return HandleIncomingMessageResult(
            newCurrentNickname = newNicknameForVm,
            privateMessageEventNick = pmEventNickForVm,
            messageProcessed = true
        )
    }
}
