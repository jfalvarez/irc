package com.jfaf.irc.domain.usecase

import android.util.Log
import com.jfaf.irc.data.database.MessageDao
import com.jfaf.irc.data.database.MessageEntity
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.data.repositories.SilentWhoisCompletionSignal
import com.jfaf.irc.data.repositories.UserMetadataRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import com.jfaf.irc.ui.viewmodels.ChatUiSnapshot
import com.jfaf.irc.ui.viewmodels.IrcMessageHandler
import com.jfaf.irc.ui.viewmodels.UiMessageType
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class HandleIncomingMessageUseCase @Inject constructor(
    private val ircMessageHandler: IrcMessageHandler,
    private val chatStateManager: ChatStateManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userMetadataRepository: UserMetadataRepository,
    private val ircRepository: IrcRepository,
    private val silentWhoisCompletionSignal: SilentWhoisCompletionSignal,
    private val messageDao: MessageDao
) {
    suspend operator fun invoke(
        parsedMessage: ParsedIrcMessage,
        currentOwnNickname: String
    ): HandleIncomingMessageResult {
        val whoisNickParam = parsedMessage.params.getOrNull(1)
        val silentWhoisCommands = setOf("311", "312", "317", "318", "319", "401")

        if (whoisNickParam != null &&
            parsedMessage.command in silentWhoisCommands &&
            ircRepository.isWhoisSilent(whoisNickParam)
        ) {
            var isEndOfSilentWhois = false
            when (parsedMessage.command) {
                "311" -> userMetadataRepository.friendSeen(whoisNickParam)
                "318", "401" -> {
                    ircRepository.completeSilentWhois(whoisNickParam)
                    isEndOfSilentWhois = true
                }
            }
            if (isEndOfSilentWhois) {
                silentWhoisCompletionSignal.signalCompletion()
            }
            return HandleIncomingMessageResult(messageProcessed = true)
        }

        val showPingPongMessages = userPreferencesRepository.showPingPongMessagesFlow.first()
        if (parsedMessage.command.equals("PING", ignoreCase = true) && !showPingPongMessages) {
            Log.d("HandleIncomingMsgUC", "PING message received and ignored for UI based on preference.")
            return HandleIncomingMessageResult(messageProcessed = false)
        }

        val snapshot = ChatUiSnapshot(
            currentNickname = currentOwnNickname,
            activeTarget = chatStateManager.activeTarget.value,
            allMessages = chatStateManager.allMessages.value,
            chatTargets = chatStateManager.chatTargets.value,
            unreadTargets = chatStateManager.unreadTargets.value,
            usersInChannel = chatStateManager.usersInChannel.value
        )

        val handlerResult = ircMessageHandler.processMessage(snapshot, parsedMessage)
        val updatedNicknameProvider = { handlerResult.newCurrentNickname ?: currentOwnNickname }
        chatStateManager.updateStateFromHandlerResult(handlerResult, updatedNicknameProvider)

        // Save private message to database
        if (handlerResult.uiMessageToAdd?.type == UiMessageType.PRIVATE_MSG_RECEIVED) {
            val senderNick = handlerResult.uiMessageToAdd.sender
            if (senderNick != null) {
                val entity = MessageEntity(
                    conversationPartnerNick = senderNick, // For a received PM, the target of the conversation is the sender
                    senderNick = senderNick,
                    content = parsedMessage.trailing ?: "",
                    timestamp = System.currentTimeMillis(),
                    isOwnMessage = false
                )
                messageDao.insertMessage(entity)
            }
        }

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
