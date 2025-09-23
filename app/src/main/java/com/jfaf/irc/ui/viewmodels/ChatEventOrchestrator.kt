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
                Log.d("ChatEventOrchestrator", "Event: MsgCmd=${parsedMessage.command}, Sender=${parsedMessage.prefix ?: "N/A"}, Target=${parsedMessage.params.firstOrNull() ?: "N/A"}, IgnoredNicks=${ignoredNicksLowercase.joinToString()}")

                val senderNick = parsedMessage.prefix?.takeWhile { it != '!' && it != '@' }

                if (senderNick != null) {
                    val senderNickLowercase = senderNick.lowercase()
                    if (senderNickLowercase in ignoredNicksLowercase) {
                        // Sender is in the ignore list
                        val command = parsedMessage.command
                        val messageTarget = parsedMessage.params.firstOrNull()

                        if (command == "PRIVMSG") {
                            // Determine if it's a private message TO US or a channel message
                            val isPrivateMessageToUs = messageTarget != null && !messageTarget.startsWith("#")

                            if (isPrivateMessageToUs) {
                                // Private message from an ignored user TO US. Send auto-reply.
                                if (senderNickLowercase !in _autoRepliedToIgnoredUsersThisSession.value) {
                                    Log.d("ChatEventOrchestrator", "Sender $senderNick (ignored) sent PM. Sending auto-reply.")
                                    if (eventListener.isConnected()) {
                                        ircRepository.sendMessage(senderNick, "Estás siendo ignorado por este usuario.")
                                    }
                                    _autoRepliedToIgnoredUsersThisSession.value += senderNickLowercase
                                } else {
                                    Log.d("ChatEventOrchestrator", "Sender $senderNick (ignored) sent PM. Auto-reply already sent.")
                                }
                            } else {
                                // Channel message from an ignored user (or malformed PRIVMSG). No auto-reply.
                                Log.d("ChatEventOrchestrator", "Sender $senderNick (ignored) sent to channel '$messageTarget'. No auto-reply.")
                            }
                            // For ANY PRIVMSG from an ignored user (private or channel), suppress the original message.
                            Log.d("ChatEventOrchestrator", "Suppressing PRIVMSG from ignored user $senderNick.")
                            return@onEach
                        } else if (command == "NOTICE") {
                            // NOTICE from an ignored user. No auto-reply. Suppress message.
                            Log.d("ChatEventOrchestrator", "Sender $senderNick (ignored) sent NOTICE to '$messageTarget'. No auto-reply. Suppressing.")
                            return@onEach
                        }
                        // Other commands (JOIN, PART, etc.) from ignored users currently pass through.
                        Log.d("ChatEventOrchestrator", "Command '$command' from ignored user $senderNick not PRIVMSG/NOTICE. Passing to UI processing.")
                    }
                }

                // If message was not returned by return@onEach (i.e., not from an ignored user sending PRIVMSG/NOTICE, or senderNick is null)
                val finalProcessingSender = senderNick ?: parsedMessage.prefix ?: "Unknown/Server"
                Log.d("ChatEventOrchestrator", "Processing message for UI: Cmd=${parsedMessage.command}, Sender=$finalProcessingSender, Target=${parsedMessage.params.firstOrNull() ?: "N/A"}")
                eventListener.processMessageForUi(parsedMessage, ignoredNicksLowercase)
            }
            .launchIn(scope)
    }

    fun resetSessionState() {
        Log.d("ChatEventOrchestrator", "Session state reset. Auto-replied list cleared.")
        _autoRepliedToIgnoredUsersThisSession.value = emptySet()
    }
}
