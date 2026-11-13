package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import androidx.compose.ui.text.AnnotatedString
import com.jfaf.irc.domain.IrcConnection
import com.jfaf.irc.domain.chat.ChatStateManager
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import com.jfaf.irc.domain.chat.command.CommandProcessor
import com.jfaf.irc.domain.chat.command.CommandResult

sealed interface SendMessageActionStatus {
    object Success : SendMessageActionStatus
    data class ViewModelActionNeeded(val commandResult: CommandResult) : SendMessageActionStatus
    object NotConnected : SendMessageActionStatus
    object BlankInput : SendMessageActionStatus
    object CannotSendToTarget : SendMessageActionStatus
}

class SendMessageOrCommandUseCase(
    private val strings: StringProvider,
    private val ircConnection: IrcConnection,
    private val chatStateManager: ChatStateManager,
    private val commandProcessor: CommandProcessor
) {
    suspend operator fun invoke(
        messageContent: String,
        currentActiveTargetFromViewModel: String?,
        currentOwnNickname: String
    ): SendMessageActionStatus {

        if (!ircConnection.connectionState.value) {
            chatStateManager.addSystemMessageToTarget(
                currentActiveTargetFromViewModel ?: ChatStateManager.SERVER_TARGET_ID,
                strings.get(Msg.SEND_NOT_CONNECTED),
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
                    chatStateManager.addSystemMessageToTarget(commandProcessingResult.targetToClear, strings.get(Msg.SEND_WINDOW_CLEARED))
                    SendMessageActionStatus.Success
                }
                is CommandResult.UnknownCommand -> {
                    chatStateManager.addSystemMessageToTarget(
                        targetForCmdSysMsg, 
                        strings.get(Msg.SEND_UNKNOWN_COMMAND, commandProcessingResult.command), 
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
                    strings.get(Msg.SEND_TO_SERVER_TAB_FORBIDDEN, ChatStateManager.SERVER_TARGET_ID),
                    isError = true
                )
                return SendMessageActionStatus.CannotSendToTarget
            } else if (currentActiveTargetFromViewModel.isNullOrBlank()) {
                chatStateManager.addSystemMessageToTarget(
                    ChatStateManager.SERVER_TARGET_ID, // Default to server if no active target
                    strings.get(Msg.SEND_NO_ACTIVE_TARGET),
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
            ircConnection.sendMessage(currentActiveTargetFromViewModel, messageContent)
            return SendMessageActionStatus.Success
        }
    }
}
