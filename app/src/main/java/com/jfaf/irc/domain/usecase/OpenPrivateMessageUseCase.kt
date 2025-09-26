package com.jfaf.irc.domain.usecase

import androidx.compose.ui.text.AnnotatedString
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import com.jfaf.irc.ui.viewmodels.UiChatMessage
import com.jfaf.irc.ui.viewmodels.UiMessageType
import javax.inject.Inject

sealed interface OpenPrivateMessageResult {
    object Success : OpenPrivateMessageResult
    data class Failure(val message: String) : OpenPrivateMessageResult
}

class OpenPrivateMessageUseCase @Inject constructor(
    private val chatStateManager: ChatStateManager,
    private val ircRepository: IrcRepository // Added IrcRepository
) {
    suspend operator fun invoke(
        nick: String, 
        currentOwnNickname: String, 
        initialMessage: String? = null
    ): OpenPrivateMessageResult {
        val targetForErrorMessage = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID

        if (nick.isBlank() || nick.startsWith("#") || nick.equals(currentOwnNickname, ignoreCase = true)) {
            val errorMessage = "Nombre de usuario inválido para chat privado: $nick"
            chatStateManager.addSystemMessageToTarget(targetForErrorMessage, errorMessage, isError = true)
            return OpenPrivateMessageResult.Failure(errorMessage)
        }
        
        chatStateManager.openPrivateMessageTarget(nick, currentOwnNickname)
        
        if (chatStateManager.activeTarget.value?.equals(nick, ignoreCase = true) == true) { 
            chatStateManager.addSystemMessageToTarget(nick, "Chat privado con $nick iniciado.")
        }

        if (!initialMessage.isNullOrBlank()) {
            if (!ircRepository.connectionState.value) {
                chatStateManager.addSystemMessageToTarget(
                    nick, 
                    "No conectado. No se puede enviar el mensaje inicial a $nick.",
                    isError = true
                )
                // Optionally return a specific failure type here, but for now, message is sufficient
            } else {
                val uiMessage = UiChatMessage(
                    fullText = "<${currentOwnNickname}> $initialMessage",
                    annotatedString = AnnotatedString("<${currentOwnNickname}> $initialMessage"),
                    type = UiMessageType.PRIVATE_MSG_SENT,
                    sender = currentOwnNickname,
                    isOwnMessage = true
                )
                chatStateManager.addLocalUiMessageToTarget(nick, uiMessage) 
                ircRepository.sendMessage(nick, initialMessage)
            }
        }
        return OpenPrivateMessageResult.Success
    }
}
