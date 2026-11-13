package com.jfaf.irc.domain.usecase

import com.jfaf.irc.log.Log
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.domain.IrcConnection
import com.jfaf.irc.data.repositories.SilentWhoisCompletionSignal
import com.jfaf.irc.domain.user.UserMetadataStore
import com.jfaf.irc.domain.user.FriendPresenceTracker
import com.jfaf.irc.domain.chat.ChatStateManager
import com.jfaf.irc.domain.chat.ChatUiSnapshot
import com.jfaf.irc.domain.chat.IrcMessageHandler
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class HandleIncomingMessageUseCase @Inject constructor(
    private val ircMessageHandler: IrcMessageHandler,
    private val chatStateManager: ChatStateManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userMetadataStore: UserMetadataStore,
    private val friendPresence: FriendPresenceTracker,
    private val ircConnection: IrcConnection,
    private val silentWhoisCompletionSignal: SilentWhoisCompletionSignal
) {
    suspend operator fun invoke(
        parsedMessage: ParsedIrcMessage,
        currentOwnNickname: String
    ): HandleIncomingMessageResult {
        val whoisNickParam = parsedMessage.params.getOrNull(1)
        val silentWhoisCommands = setOf("311", "312", "317", "318", "319", "401")

        if (whoisNickParam != null &&
            parsedMessage.command in silentWhoisCommands &&
            ircConnection.isWhoisSilent(whoisNickParam)
        ) {
            var isEndOfSilentWhois = false
            when (parsedMessage.command) {
                "311" -> friendPresence.friendSeen(whoisNickParam)
                "318", "401" -> {
                    ircConnection.completeSilentWhois(whoisNickParam)
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

        val newNicknameForVm = handlerResult.newCurrentNickname
        var pmEventNickForVm: String? = null

        if (handlerResult.privateMessageEventNick != null) {
            val ignoredUsersLowercaseFromPrefs = userMetadataStore.ignoredUsersFlow.first()
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
