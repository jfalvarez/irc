package com.jfaf.irc.ui.viewmodels

import android.util.Log
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.data.repositories.UserMetadataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

interface ChatEventListener {
    fun processMessageForUi(parsedMessage: ParsedIrcMessage)
    fun emitPrivateMessageEvent(nick: String)
    // isConnected() removed from interface
}

class ChatEventOrchestrator(
    private val ircRepository: IrcRepository,
    private val userMetadataRepository: UserMetadataRepository,
    private val eventListener: ChatEventListener,
    private val scope: CoroutineScope
) {

    private val _autoRepliedToIgnoredUsersThisSession = MutableStateFlow<Set<String>>(emptySet())

    fun startObservingRawMessages() {
        ircRepository.incomingMessages
            .combine(userMetadataRepository.ignoredUsersFlow) { parsedMessage, currentIgnoredNicks ->
                Pair(parsedMessage, currentIgnoredNicks.map { it.lowercase() }.toSet())
            }
            .onEach { (parsedMessage, ignoredNicksLowercase) -> 
                Log.d("ChatEventOrchestrator", "Event: MsgCmd=${parsedMessage.command}, Sender=${parsedMessage.prefix ?: "N/A"}, Target=${parsedMessage.params.firstOrNull() ?: "N/A"}, IgnoredNicks=${ignoredNicksLowercase.joinToString()}")

                val senderNick = parsedMessage.prefix?.takeWhile { it != '!' && it != '@' }

                if (senderNick != null) {
                    val senderNickLowercase = senderNick.lowercase()
                    if (senderNickLowercase in ignoredNicksLowercase) {
                        val command = parsedMessage.command
                        val messageTarget = parsedMessage.params.firstOrNull()

                        if (command == "PRIVMSG") {
                            val isPrivateMessageToUs = messageTarget != null && !messageTarget.startsWith("#")
                            if (isPrivateMessageToUs) {
                                if (senderNickLowercase !in _autoRepliedToIgnoredUsersThisSession.value) {
                                    Log.d("ChatEventOrchestrator", "Sender $senderNick (ignored) sent PM. Sending auto-reply.")
                                    // Use ircRepository.connectionState.value directly
                                    if (ircRepository.connectionState.value) {
                                        ircRepository.sendMessage(senderNick, "Estás siendo ignorado por este usuario.")
                                    }
                                    _autoRepliedToIgnoredUsersThisSession.value += senderNickLowercase
                                } else {
                                    Log.d("ChatEventOrchestrator", "Sender $senderNick (ignored) sent PM. Auto-reply already sent.")
                                }
                            }
                            Log.d("ChatEventOrchestrator", "Suppressing PRIVMSG from ignored user $senderNick.")
                            return@onEach
                        } else if (command == "NOTICE") {
                            Log.d("ChatEventOrchestrator", "Sender $senderNick (ignored) sent NOTICE to '$messageTarget'. No auto-reply. Suppressing.")
                            return@onEach
                        }
                        Log.d("ChatEventOrchestrator", "Command '$command' from ignored user $senderNick not PRIVMSG/NOTICE. Passing to UI processing.")
                    }
                }

                val finalProcessingSender = senderNick ?: parsedMessage.prefix ?: "Unknown/Server"
                Log.d("ChatEventOrchestrator", "Processing message for UI: Cmd=${parsedMessage.command}, Sender=$finalProcessingSender, Target=${parsedMessage.params.firstOrNull() ?: "N/A"}")
                eventListener.processMessageForUi(parsedMessage)
            }
            .launchIn(scope)
    }

    fun resetSessionState() {
        Log.d("ChatEventOrchestrator", "Session state reset. Auto-replied list cleared.")
        _autoRepliedToIgnoredUsersThisSession.value = emptySet()
    }
}
