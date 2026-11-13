package com.jfaf.irc.domain.chat.command

import androidx.compose.ui.text.AnnotatedString
import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.domain.IrcSender
import com.jfaf.irc.domain.chat.ChatStateManager
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType

class CommandProcessor(
    private val ircRepository: IrcSender,
    private val chatStateManager: ChatStateManager,
    private val strings: StringProvider
) {

    private fun parseCommandAndArgs(commandLine: String): Pair<String, String?> {
        val commandAndArgsString = commandLine.drop(1).trim()
        val parts = commandAndArgsString.split(" ", limit = 2)
        val command = parts[0].lowercase()
        val args = parts.getOrNull(1)
        return command to args
    }

    fun process(commandLine: String, currentActiveTarget: String?, currentNickname: String): CommandResult {
        val (command, args) = parseCommandAndArgs(commandLine)
        val targetForSysMsgOnError = currentActiveTarget ?: ChatStateManager.SERVER_TARGET_ID

        if (command.isBlank()) {
            return CommandResult.InvalidArguments(strings.get(Msg.CMD_INVALID))
        }

        return when (command) {
            "me" -> executeMeCommand(args, currentActiveTarget, currentNickname, targetForSysMsgOnError)
            "nick" -> executeNickCommand(args, targetForSysMsgOnError)
            "join" -> processJoinCommand(args, targetForSysMsgOnError)
            "part" -> processPartCommand(args, currentActiveTarget)
            "quit" -> CommandResult.Quit(args)
            "away" -> executeAwayCommand(args, targetForSysMsgOnError)
            "msg" -> executeMsgCommand(args, currentNickname, targetForSysMsgOnError)
            "query" -> processQueryCommand(args, currentNickname, targetForSysMsgOnError)
            "topic" -> executeTopicCommand(args, currentActiveTarget, targetForSysMsgOnError)
            "clear" -> processClearCommand(currentActiveTarget, targetForSysMsgOnError)
            "help" -> processHelpCommand(targetForSysMsgOnError)
            else -> CommandResult.UnknownCommand(command)
        }
    }

    private fun executeMeCommand(args: String?, currentActiveTarget: String?, currentNickname: String, targetForSysMsgOnError: String): CommandResult {
        if (args.isNullOrBlank()) {
            return CommandResult.InvalidArguments(strings.get(Msg.CMD_ME_USAGE))
        }
        if (currentActiveTarget.isNullOrBlank() || currentActiveTarget == ChatStateManager.SERVER_TARGET_ID) {
            return CommandResult.ShowSystemMessage(targetForSysMsgOnError, strings.get(Msg.CMD_ME_WRONG_CONTEXT), isError = true)
        }
        val actionMessage = "* $currentNickname $args"
        val uiMessage = UiChatMessage(
            fullText = actionMessage,
            annotatedString = AnnotatedString(actionMessage),
            type = UiMessageType.ACTION_MSG,
            sender = currentNickname,
            isOwnMessage = true
        )
        chatStateManager.addLocalUiMessageToTarget(currentActiveTarget, uiMessage)
        ircRepository.sendMessage(currentActiveTarget, "\u0001ACTION $args\u0001")
        return CommandResult.Handled
    }

    private fun executeNickCommand(args: String?, targetForSysMsgOnError: String): CommandResult {
        if (args.isNullOrBlank()) {
            return CommandResult.InvalidArguments(strings.get(Msg.CMD_NICK_USAGE))
        }
        chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, strings.get(Msg.CMD_NICK_TRYING, args))
        ircRepository.sendRawCommand("NICK $args")
        return CommandResult.Handled
    }

    private fun processJoinCommand(args: String?, targetForSysMsgOnError: String): CommandResult {
        val cmdArgs = args?.split(" ", limit = 2)
        val channel = cmdArgs?.getOrNull(0)
        val key = cmdArgs?.getOrNull(1)
        return if (channel.isNullOrBlank()) {
            CommandResult.InvalidArguments(strings.get(Msg.CMD_JOIN_USAGE))
        } else {
            CommandResult.JoinChannel(channel, key)
        }
    }

    private fun processPartCommand(args: String?, currentActiveTarget: String?): CommandResult {
        var channelToPart: String? = null
        var partMsg: String? = null

        if (!args.isNullOrBlank()) {
            val firstArg = args.split(" ").first()
            if (firstArg.startsWith("#")) {
                channelToPart = firstArg
                partMsg = args.substring(firstArg.length).trimStart().ifEmpty { null }
            } else {
                partMsg = args
            }
        } 
        // If channelToPart is null, ViewModel will use currentActiveTarget
        return CommandResult.PartChannel(channelToPart, partMsg)
    }

    private fun executeAwayCommand(args: String?, targetForSysMsgOnError: String): CommandResult {
        if (args.isNullOrBlank()) {
            ircRepository.sendRawCommand("AWAY")
            chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, strings.get(Msg.CMD_AWAY_OFF))
        } else {
            ircRepository.sendRawCommand("AWAY :$args")
            chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, strings.get(Msg.CMD_AWAY_ON, args))
        }
        return CommandResult.Handled
    }

    private fun executeMsgCommand(args: String?, currentNickname: String, targetForSysMsgOnError: String): CommandResult {
        val msgParts = args?.split(" ", limit = 2)
        val targetName = msgParts?.getOrNull(0)
        val messageText = msgParts?.getOrNull(1)

        if (targetName.isNullOrBlank() || messageText.isNullOrBlank()) {
            return CommandResult.InvalidArguments(strings.get(Msg.CMD_MSG_USAGE))
        }
        if (targetName.equals(currentNickname, ignoreCase = true)){
            return CommandResult.ShowSystemMessage(targetForSysMsgOnError, strings.get(Msg.CMD_MSG_SELF), isError = true)
        }
        if (targetName.equals(ChatStateManager.SERVER_TARGET_ID, ignoreCase = true)){
            return CommandResult.ShowSystemMessage(targetForSysMsgOnError, strings.get(Msg.CMD_MSG_SERVER_TAB, ChatStateManager.SERVER_TARGET_ID), isError = true)
        }

        val isChannelMsg = targetName.startsWith("#")
        if (!isChannelMsg) {
            // Let ViewModel handle PM target creation if needed via ChatStateManager
            // For now, CommandProcessor assumes target exists or will be handled by ChatStateManager based on sendMessage path
        }

        val uiMessage = UiChatMessage(
            fullText = "<${currentNickname}> $messageText",
            annotatedString = AnnotatedString("<${currentNickname}> $messageText"),
            type = if (isChannelMsg) UiMessageType.CHANNEL_MSG_SENT else UiMessageType.PRIVATE_MSG_SENT,
            sender = currentNickname,
            isOwnMessage = true
        )
        // The ViewModel will use its own sendMessage logic which already handles PM target creation in ChatStateManager
        // So, this local message addition might be redundant or could be moved to ViewModel after command processing.
        // For now, keeping it to mimic original behavior closely for commands handled directly here.
        chatStateManager.addLocalUiMessageToTarget(targetName, uiMessage)
        ircRepository.sendMessage(targetName, messageText)
        chatStateManager.addSystemMessageToTarget(targetName, strings.get(Msg.CMD_MSG_SENT, targetName)) // Feedback for /msg
        return CommandResult.Handled
    }

    private fun processQueryCommand(args: String?, currentNickname: String, targetForSysMsgOnError: String): CommandResult {
        val queryParts = args?.split(" ", limit = 2)
        val nick = queryParts?.getOrNull(0)
        val initialMessage = queryParts?.getOrNull(1)

        if (nick.isNullOrBlank()) {
            return CommandResult.InvalidArguments(strings.get(Msg.CMD_QUERY_USAGE))
        }
        if (nick.equals(currentNickname, ignoreCase = true)){
             return CommandResult.ShowSystemMessage(targetForSysMsgOnError, strings.get(Msg.CMD_QUERY_SELF), isError = true)
        }
        if (nick.startsWith("#")){
            return CommandResult.ShowSystemMessage(targetForSysMsgOnError, strings.get(Msg.CMD_QUERY_CHANNEL), isError = true)
        }
        return CommandResult.OpenQuery(nick, initialMessage)
    }

    private fun executeTopicCommand(args: String?, currentActiveTarget: String?, targetForSysMsgOnError: String): CommandResult {
        var targetChannel: String? = null
        var newTopic: String? = null
        
        val firstArg = args?.split(" ")?.firstOrNull()

        if (firstArg?.startsWith("#") == true) {
            targetChannel = firstArg
            newTopic = args.substring(firstArg.length).trimStart().ifEmpty { null }
        } else if (currentActiveTarget?.startsWith("#") == true) {
            targetChannel = currentActiveTarget
            newTopic = args?.ifEmpty { null } 
        } else {
            return CommandResult.InvalidArguments(strings.get(Msg.CMD_TOPIC_USAGE))
        }

        if (targetChannel == null) { 
             return CommandResult.ShowSystemMessage(targetForSysMsgOnError, strings.get(Msg.CMD_TOPIC_NO_CHANNEL), isError = true)
        }

        if (newTopic != null) {
            ircRepository.sendRawCommand("TOPIC $targetChannel :$newTopic")
            chatStateManager.addSystemMessageToTarget(targetChannel, strings.get(Msg.CMD_TOPIC_TRYING, newTopic))
        } else {
            ircRepository.sendRawCommand("TOPIC $targetChannel")
            chatStateManager.addSystemMessageToTarget(targetChannel, strings.get(Msg.CMD_TOPIC_REQUESTING, targetChannel))
        }
        return CommandResult.Handled
    }

    private fun processClearCommand(currentActiveTarget: String?, targetForSysMsgOnError: String): CommandResult {
        return if (!currentActiveTarget.isNullOrBlank()) {
            CommandResult.ClearWindow(currentActiveTarget)
        } else {
            CommandResult.ShowSystemMessage(targetForSysMsgOnError, strings.get(Msg.CMD_CLEAR_NO_WINDOW), isError = true)
        }
    }

    private fun processHelpCommand(targetForSysMsgOnError: String): CommandResult {
        val helpMessage = strings.get(Msg.CMD_HELP)
        return CommandResult.ShowSystemMessage(targetForSysMsgOnError, helpMessage)
    }
}
