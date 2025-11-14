package com.jfaf.irc.domain.usecase

import androidx.compose.ui.text.AnnotatedString
import com.jfaf.irc.data.database.MessageDao
import com.jfaf.irc.data.database.MessageEntity
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import com.jfaf.irc.ui.viewmodels.UiChatMessage
import com.jfaf.irc.ui.viewmodels.UiMessageType
import com.jfaf.irc.ui.viewmodels.command.CommandProcessor
import com.jfaf.irc.ui.viewmodels.command.CommandResult
import javax.inject.Inject

sealed interface SendMessageActionStatus {
    object Success : SendMessageActionStatus
    data class ViewModelActionNeeded(val commandResult: CommandResult) : SendMessageActionStatus
    object NotConnected : SendMessageActionStatus
    object BlankInput : SendMessageActionStatus
    object CannotSendToTarget : SendMessageActionStatus
}

class SendMessageOrCommandUseCase @Inject constructor(
    private val ircRepository: IrcRepository,
    private val chatStateManager: ChatStateManager,
    private val commandProcessor: CommandProcessor,
    private val messageDao: MessageDao
) {
    suspend operator fun invoke(
        messageContent: String,
        currentActiveTargetFromViewModel: String?,
        currentOwnNickname: String
    ): SendMessageActionStatus {

        if (!ircRepository.connectionState.value) {
            chatStateManager.addSystemMessageToTarget(
                currentActiveTargetFromViewModel ?: ChatStateManager.SERVER_TARGET_ID,
                "No conectado. No se puede enviar el mensaje/comando.",
                isError = true
            )
            return SendMessageActionStatus.NotConnected
        }

        if (messageContent.isBlank()) {
            return SendMessageActionStatus.BlankInput
        }

        if (messageContent.startsWith("/")) {
            val commandProcessingResult = commandProcessor.process(
                commandLine = messageContent,
                currentActiveTarget = currentActiveTargetFromViewModel,
                currentNickname = currentOwnNickname
            )

            val targetForCmdSysMsg = currentActiveTargetFromViewModel ?: ChatStateManager.SERVER_TARGET_ID

            return when (commandProcessingResult) {
                is CommandResult.Handled -> SendMessageActionStatus.Success
                is CommandResult.ShowSystemMessage -> {
                    chatStateManager.addSystemMessageToTarget(
                        commandProcessingResult.target,
                        commandProcessingResult.message,
                        commandProcessingResult.isError
                    )
                    SendMessageActionStatus.Success
                }
                is CommandResult.ClearWindow -> {
                    chatStateManager.clearMessagesForTarget(commandProcessingResult.targetToClear)
                    chatStateManager.addSystemMessageToTarget(commandProcessingResult.targetToClear, "Ventana limpiada.")
                    SendMessageActionStatus.Success
                }
                is CommandResult.UnknownCommand -> {
                    chatStateManager.addSystemMessageToTarget(
                        targetForCmdSysMsg, 
                        "Comando '/${commandProcessingResult.command}' desconocido. Escribe /help para ver los comandos disponibles.", 
                        isError = true
                    )
                    SendMessageActionStatus.Success 
                }
                is CommandResult.InvalidArguments -> {
                    chatStateManager.addSystemMessageToTarget(
                        targetForCmdSysMsg, 
                        commandProcessingResult.usageHint, 
                        isError = true
                    )
                    SendMessageActionStatus.Success
                }
                is CommandResult.JoinChannel,
                is CommandResult.PartChannel,
                is CommandResult.OpenQuery,
                is CommandResult.Quit -> SendMessageActionStatus.ViewModelActionNeeded(commandProcessingResult)
            }
        } else {
            // Regular message
            if (currentActiveTargetFromViewModel == ChatStateManager.SERVER_TARGET_ID) {
                chatStateManager.addSystemMessageToTarget(
                    ChatStateManager.SERVER_TARGET_ID,
                    "No puedes enviar mensajes a la pestaña '${ChatStateManager.SERVER_TARGET_ID}'. Utiliza comandos (ej: /join, /nick) o abre un canal/PM.",
                    isError = true
                )
                return SendMessageActionStatus.CannotSendToTarget
            } else if (currentActiveTargetFromViewModel.isNullOrBlank()) {
                chatStateManager.addSystemMessageToTarget(
                    ChatStateManager.SERVER_TARGET_ID, // Default to server if no active target
                    "No hay un target activo para enviar el mensaje.",
                    isError = true
                )
                return SendMessageActionStatus.CannotSendToTarget
            }

            // Send regular message to valid target
            val isChannelMessage = currentActiveTargetFromViewModel.startsWith("#")
            val uiMessage = UiChatMessage(
                fullText = "<${currentOwnNickname}> $messageContent",
                annotatedString = AnnotatedString("<${currentOwnNickname}> $messageContent"),
                type = if (isChannelMessage) UiMessageType.CHANNEL_MSG_SENT else UiMessageType.PRIVATE_MSG_SENT,
                sender = currentOwnNickname,
                isOwnMessage = true
            )
            chatStateManager.addLocalUiMessageToTarget(currentActiveTargetFromViewModel, uiMessage)
            ircRepository.sendMessage(currentActiveTargetFromViewModel, messageContent)

            if (!isChannelMessage) {
                val messageEntity = MessageEntity(
                    conversationPartnerNick = currentActiveTargetFromViewModel,
                    senderNick = currentOwnNickname,
                    content = messageContent,
                    timestamp = System.currentTimeMillis(),
                    isOwnMessage = true
                )
                messageDao.insertMessage(messageEntity)
            }

            return SendMessageActionStatus.Success
        }
    }
}
