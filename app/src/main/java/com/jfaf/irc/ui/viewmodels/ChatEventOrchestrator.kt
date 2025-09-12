package com.jfaf.irc.ui.viewmodels

import android.util.Log
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.repositories.IrcRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

interface ChatEventListener {
    fun processMessageForUi(parsedMessage: ParsedIrcMessage, ignoredUsersLowercase: Set<String>)
    fun emitPrivateMessageEvent(nick: String)
    fun isConnected(): Boolean
}

class ChatEventOrchestrator(
    private val ircRepository: IrcRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val eventListener: ChatEventListener,
    private val scope: CoroutineScope
) {

    private val _autoRepliedToIgnoredUsersThisSession = MutableStateFlow<Set<String>>(emptySet())

    fun startObservingRawMessages() {
        ircRepository.incomingMessages
            .combine(userPreferencesRepository.ignoredUsersFlow) { parsedMessage, currentIgnoredNicks ->
                // Ensure ignored nicks are lowercase for consistent checking
                Pair(parsedMessage, currentIgnoredNicks.map { it.lowercase() }.toSet())
            }
            .onEach { (parsedMessage, ignoredNicksLowercase) ->
                Log.d("ChatEventOrchestrator", "Combined Event: Message: ${parsedMessage.command}, Ignored: ${ignoredNicksLowercase.joinToString()}")

                val senderNick = parsedMessage.prefix?.takeWhile { it != '!' && it != '@' }
                if (senderNick != null) {
                    val senderNickLowercase = senderNick.lowercase()
                    if (senderNickLowercase in ignoredNicksLowercase) {
                        val command = parsedMessage.command
                        if (command == "PRIVMSG" || command == "NOTICE") {
                            if (senderNickLowercase !in _autoRepliedToIgnoredUsersThisSession.value) {
                                Log.d("ChatEventOrchestrator", "Ignoring incoming $command from ignored user: $senderNick. Sending auto-reply for the first time this session.")
                                if (eventListener.isConnected()) { // Check connection via listener
                                    ircRepository.sendMessage(senderNick, "Estás siendo ignorado por este usuario.")
                                }
                                _autoRepliedToIgnoredUsersThisSession.value = _autoRepliedToIgnoredUsersThisSession.value + senderNickLowercase
                            } else {
                                Log.d("ChatEventOrchestrator", "Ignoring incoming $command from ignored user: $senderNick. Auto-reply already sent this session.")
                            }
                            // Notify listener about private message event, even if auto-reply is suppressed by rate limit
                            // This is because the original logic in MainViewModel for _incomingPrivateMessageEvent was outside the ignore check
                            // but the new requirement is to suppress it for ignored users.
                            // The current logic correctly DOES NOT call emitPrivateMessageEvent for ignored users.
                            // If it were needed for ignored users (e.g. to show a suppressed message indicator), it would go here.

                            return@onEach // Skip further processing for this message
                        }
                    }
                }
                Log.d("ChatEventOrchestrator", "Message from ${senderNick ?: "UnknownSender"} not ignored or not PRIVMSG/NOTICE. Processing with listener.")
                // Pass to listener to process for UI and potentially emit private message events
                eventListener.processMessageForUi(parsedMessage, ignoredNicksLowercase)
            }
            .launchIn(scope)
    }

    fun resetSessionState() {
        Log.d("ChatEventOrchestrator", "Session state reset. Auto-replied list cleared.")
        _autoRepliedToIgnoredUsersThisSession.value = emptySet()
    }
}
