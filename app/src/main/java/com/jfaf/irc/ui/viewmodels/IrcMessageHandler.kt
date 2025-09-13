package com.jfaf.irc.ui.viewmodels

import android.util.Log
import com.jfaf.irc.data.model.ParsedIrcMessage
import javax.inject.Inject

// Data classes used by IrcMessageHandler
data class ChatUiSnapshot(
    val currentNickname: String,
    val activeTarget: String?,
    val allMessages: Map<String, List<UiChatMessage>>,
    val chatTargets: List<String>,
    val unreadTargets: Set<String>
)

data class ChatUpdateResult(
    val newCurrentNickname: String,
    val newActiveTarget: String?,
    val newAllMessages: Map<String, List<UiChatMessage>>,
    val newChatTargets: List<String>,
    val newUnreadTargets: Set<String>,
    val uiMessageToAdd: UiChatMessage? = null,
    val targetForUiMessage: String? = null,
    val privateMessageEventNick: String? = null,
    val ownNickChangedTo: String? = null
)

data class FiveTuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

class IrcMessageHandler @Inject constructor() {

    private val SERVER_TARGET_ID = "Servidor"
    private val maxUiMessagesPerTarget = 150

    private fun extractImageUrl(text: String): String? {
        Log.d("extractImageUrl", "Input text: '$text'")
        // MODIFICADO: Eliminado RegexOption.IGNORE_CASE
        val urlRegex = "(https|http)://.+?\\.(png|jpg|jpeg|gif|webp)".toRegex() 
        val matchResult = urlRegex.find(text)
        Log.d("extractImageUrl", "Regex pattern: '${urlRegex.pattern}'")
        Log.d("extractImageUrl", "Match result: '${matchResult?.value}'")
        return matchResult?.value
    }

    private fun ensureServerTargetIsFirst(targets: List<String>): List<String> {
        val otherTargets = targets.asSequence().filterNot { it == SERVER_TARGET_ID }.distinct().toList()
        return listOf(SERVER_TARGET_ID) + otherTargets
    }

    private fun addMessageToTargetInternal(
        target: String,
        message: UiChatMessage,
        currentMessages: MutableMap<String, List<UiChatMessage>>
    ) {
        Log.d("IrcMessageHandler.AddMsg", "Target: '$target', Msg: '${message.fullText}', Img: ${message.imageUrl}, List size before: ${currentMessages[target]?.size ?: 0}")
        val currentMessagesForTarget = currentMessages[target] ?: emptyList()
        val updatedMessagesForTarget = (currentMessagesForTarget + message).takeLast(maxUiMessagesPerTarget)
        currentMessages[target] = updatedMessagesForTarget
        Log.d("IrcMessageHandler.AddMsg", "Target: '$target', List size after: ${currentMessages[target]?.size}")
    }

    fun processMessage(
        snapshot: ChatUiSnapshot,
        parsedMessage: ParsedIrcMessage
    ): ChatUpdateResult {
        Log.d("IrcMessageHandler", "Processing: ${parsedMessage.rawLine}")

        var currentNickname = snapshot.currentNickname
        var activeTarget = snapshot.activeTarget
        val allMessages = snapshot.allMessages.toMutableMap()
        var chatTargets = snapshot.chatTargets.toMutableList()
        var unreadTargets = snapshot.unreadTargets.toMutableSet()

        var uiMessageToAdd: UiChatMessage? = null
        var targetForUiMessage: String? = null
        var privateMessageEventNick: String? = null
        var ownNickChangedTo: String? = null

        val command = parsedMessage.command

        when (command) {
            "PRIVMSG" -> {
                val result = handlePrivmsgInternal(
                    parsedMessage,
                    currentNickname,
                    activeTarget,
                    chatTargets,
                    unreadTargets
                )
                targetForUiMessage = result.first
                uiMessageToAdd = result.second
                result.third?.let { privateMessageEventNick = it }
                result.fourth?.let { chatTargets = it.toMutableList() }
                result.fifth?.let { unreadTargets = it.toMutableSet() }
            }
            "NOTICE" -> {
                val result = handleNoticeInternal(parsedMessage, currentNickname, activeTarget, unreadTargets)
                targetForUiMessage = result.first
                uiMessageToAdd = result.second
                result.third?.let { unreadTargets = it.toMutableSet() }
            }
            "JOIN" -> {
                val result = handleJoinInternal(parsedMessage, currentNickname, chatTargets, unreadTargets)
                targetForUiMessage = result.first
                uiMessageToAdd = result.second
                activeTarget = result.third ?: activeTarget
                result.fourth?.let { chatTargets = it.toMutableList() }
                result.fifth?.let { unreadTargets = it.toMutableSet() }
            }
            "PART" -> {
                val result = handlePartInternal(parsedMessage, currentNickname, activeTarget, chatTargets, unreadTargets)
                targetForUiMessage = result.first
                uiMessageToAdd = result.second
                activeTarget = result.third ?: activeTarget
                result.fourth?.let { chatTargets = it.toMutableList() }
                result.fifth?.let { unreadTargets = it.toMutableSet() }
            }
            "QUIT" -> {
                handleQuitInternal(parsedMessage, chatTargets, allMessages)
            }
            "NICK" -> {
                val nickChangeResult = handleNickInternal(parsedMessage, currentNickname, activeTarget, chatTargets, unreadTargets, allMessages)
                currentNickname = nickChangeResult.newNickname ?: currentNickname
                activeTarget = nickChangeResult.newActiveTarget ?: activeTarget
                chatTargets = nickChangeResult.newChatTargets.toMutableList()
                unreadTargets = nickChangeResult.newUnreadTargets.toMutableSet()
                if (nickChangeResult.ownNickChanged) {
                    ownNickChangedTo = currentNickname
                }
            }
            "MODE" -> {
                val result = handleModeInternal(parsedMessage, currentNickname, activeTarget)
                targetForUiMessage = result.first
                uiMessageToAdd = result.second
            }
            in "001".."399" -> {
                val result = handleNumericReplyInternal(parsedMessage, currentNickname)
                targetForUiMessage = result.first
                uiMessageToAdd = result.second
                currentNickname = result.third ?: currentNickname
                if (command == "001" && result.third != null) {
                     ownNickChangedTo = currentNickname
                }
            }
            in "400".."599" -> {
                val result = handleErrorReplyInternal(parsedMessage, activeTarget)
                targetForUiMessage = result.first
                uiMessageToAdd = result.second
            }
            else -> {
                val result = handleOtherCommandInternal(parsedMessage, activeTarget)
                targetForUiMessage = result.first
                uiMessageToAdd = result.second
            }
        }

        if (targetForUiMessage != null && uiMessageToAdd != null) {
            addMessageToTargetInternal(targetForUiMessage, uiMessageToAdd, allMessages)
        }
        
        Log.d("IrcMessageHandler.Result", "Returning: activeTarget='${activeTarget}', nick='${currentNickname}', allMessages keys='${allMessages.keys.joinToString()}', chatTargets='${chatTargets.joinToString()}', unread='${unreadTargets.joinToString()}', uiMsgToAdd='${uiMessageToAdd?.fullText}', targetForUiMsg='${targetForUiMessage}'")

        return ChatUpdateResult(
            newCurrentNickname = currentNickname,
            newActiveTarget = activeTarget,
            newAllMessages = allMessages.toMap(),
            newChatTargets = ensureServerTargetIsFirst(chatTargets.toList()),
            newUnreadTargets = unreadTargets.toSet(),
            uiMessageToAdd = uiMessageToAdd,
            targetForUiMessage = targetForUiMessage,
            privateMessageEventNick = privateMessageEventNick,
            ownNickChangedTo = ownNickChangedTo
        )
    }

    private fun handlePrivmsgInternal(
        parsedMessage: ParsedIrcMessage,
        currentNickname: String,
        activeTarget: String?,
        currentChatTargets: List<String>,
        currentUnreadTargetsParam: Set<String>
    ): FiveTuple<String?, UiChatMessage?, String?, List<String>?, Set<String>?> {
        val sender = parsedMessage.senderNickname
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        val msgTarget = params.firstOrNull() ?: return FiveTuple(null, null, null, null, null)
        val content = trailing ?: ""
        val isToChannel = msgTarget.startsWith("#")
        val currentIsOwn = sender?.equals(currentNickname, ignoreCase = true) == true
        val determinedTargetKey = if (isToChannel) msgTarget else if (currentIsOwn) msgTarget else sender

        var pmEventNick: String? = null
        var updatedChatTargets: List<String>? = null
        var updatedUnreadTargets: Set<String>? = null

        if (determinedTargetKey != null) {
            if (!currentIsOwn) {
                if (!determinedTargetKey.equals(activeTarget, ignoreCase = true)) {
                    updatedUnreadTargets = currentUnreadTargetsParam + determinedTargetKey
                }
                if (!isToChannel) {
                    pmEventNick = determinedTargetKey
                }
            }

            if (determinedTargetKey != SERVER_TARGET_ID && !isToChannel && !currentIsOwn) {
                if (!currentChatTargets.any { it.equals(determinedTargetKey, ignoreCase = true) }) {
                    updatedChatTargets = currentChatTargets + determinedTargetKey
                }
            }
        }

        val messageText = when {
            isToChannel -> "<${sender}> $content"
            currentIsOwn -> "<${currentNickname}> $content" 
            else -> "<${sender}> $content"
        }
        val imageUrl = extractImageUrl(content)
        val newUiMsg = UiChatMessage(
            fullText = messageText,
            type = when {
                currentIsOwn && isToChannel -> UiMessageType.CHANNEL_MSG_SENT 
                currentIsOwn && !isToChannel -> UiMessageType.PRIVATE_MSG_SENT 
                !currentIsOwn && isToChannel -> UiMessageType.CHANNEL_MSG_RECEIVED
                else -> UiMessageType.PRIVATE_MSG_RECEIVED
            },
            sender = sender,
            isOwnMessage = currentIsOwn,
            imageUrl = imageUrl
        )
        return FiveTuple(determinedTargetKey, newUiMsg, pmEventNick, updatedChatTargets, updatedUnreadTargets)
    }

    private fun handleNoticeInternal(
        parsedMessage: ParsedIrcMessage,
        currentNickname: String,
        activeTarget: String?,
        currentUnreadTargets: Set<String>
    ): Triple<String?, UiChatMessage?, Set<String>?> {
        val sender = parsedMessage.senderNickname
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        val noticeTargetParam = params.firstOrNull()
        val from = sender ?: parsedMessage.prefix ?: "Server"
        val content = trailing ?: params.joinToString(" ")
        
        val determinedTargetKey = if (noticeTargetParam?.equals(currentNickname, ignoreCase = true) == true && sender != null) sender else activeTarget ?: SERVER_TARGET_ID
        val imageUrl = extractImageUrl(content)
        val newUiMsg = UiChatMessage(
            fullText = "-$from- $content", 
            type = UiMessageType.NOTICE, 
            sender = from,
            imageUrl = imageUrl
        )

        var updatedUnreadTargets: Set<String>? = null
        if (determinedTargetKey != null && sender != null && noticeTargetParam?.equals(currentNickname, ignoreCase = true) == true && !determinedTargetKey.equals(activeTarget, ignoreCase = true)) {
            updatedUnreadTargets = currentUnreadTargets + determinedTargetKey
        }
        return Triple(determinedTargetKey, newUiMsg, updatedUnreadTargets)
    }

    private fun handleJoinInternal(
        parsedMessage: ParsedIrcMessage,
        currentNickname: String,
        currentChatTargets: List<String>,
        currentUnreadTargets: Set<String>
    ): FiveTuple<String?, UiChatMessage?, String?, List<String>?, Set<String>?> { 
        val sender = parsedMessage.senderNickname
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        val channel = trailing ?: params.firstOrNull() ?: return FiveTuple(null, null, null, null, null)
        var newActiveTarget: String? = null
        var updatedChatTargets: List<String>? = null
        var updatedUnreadTargets: Set<String>? = null

        if (sender?.equals(currentNickname, ignoreCase = true) == true) {
            if (!currentChatTargets.any { it.equals(channel, ignoreCase = true) }) {
                updatedChatTargets = currentChatTargets + channel
            }
            newActiveTarget = channel
            if (currentUnreadTargets.contains(channel)) {
                updatedUnreadTargets = currentUnreadTargets - channel
            }
        }
        val newUiMsg = UiChatMessage("* ${sender ?: "Alguien"} ha entrado a $channel", UiMessageType.JOIN_PART_QUIT, sender)
        return FiveTuple(channel, newUiMsg, newActiveTarget, updatedChatTargets, updatedUnreadTargets)
    }

    private fun handlePartInternal(
        parsedMessage: ParsedIrcMessage,
        currentNickname: String,
        currentActiveTarget: String?,
        currentChatTargets: List<String>,
        currentUnreadTargets: Set<String>
    ): FiveTuple<String?, UiChatMessage?, String?, List<String>?, Set<String>?> { 
        val sender = parsedMessage.senderNickname
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        var channelName = params.firstOrNull()
        if (channelName.isNullOrBlank() && !trailing.isNullOrBlank() && trailing.startsWith("#")) {
            channelName = trailing.split(" ")[0]
        }
        if (channelName.isNullOrBlank()) {
            Log.w("IrcMessageHandler", "PART sin nombre de canal. Params: $params, Trailing: $trailing")
            return FiveTuple(null, null, null, null, null)
        }

        val reasonPart = if (trailing != channelName) trailing?.substringAfter(channelName)?.trim() else null
        val reasonMsgContent = reasonPart?.let { if (it.startsWith(":")) it.substring(1) else it } ?: ""
        val reasonMsg = if (reasonMsgContent.isNotBlank()) " ($reasonMsgContent)" else ""
        val newUiMsg = UiChatMessage("* ${sender ?: "Alguien"} ha salido de $channelName$reasonMsg", UiMessageType.JOIN_PART_QUIT, sender)

        var updatedActiveTarget: String? = null
        var updatedChatTargets: List<String>? = null
        var updatedUnreadTargets: Set<String>? = null

        if (sender?.equals(currentNickname, ignoreCase = true) == true) {
            if (currentChatTargets.any { it.equals(channelName, ignoreCase = true) }) {
                updatedChatTargets = currentChatTargets.filterNot { it.equals(channelName, ignoreCase = true) }
                if (currentUnreadTargets.contains(channelName)) {
                    updatedUnreadTargets = currentUnreadTargets - channelName
                }
                if (currentActiveTarget?.equals(channelName, ignoreCase = true) == true) {
                    updatedActiveTarget = updatedChatTargets.firstOrNull() ?: SERVER_TARGET_ID
                }
            }
        }
        return FiveTuple(channelName, newUiMsg, updatedActiveTarget, updatedChatTargets, updatedUnreadTargets)
    }

    private fun handleQuitInternal(
        parsedMessage: ParsedIrcMessage,
        currentChatTargets: List<String>,
        allMessages: MutableMap<String, List<UiChatMessage>> 
    ) {
        val sender = parsedMessage.senderNickname
        val trailing = parsedMessage.trailing
        val reason = trailing?.let { " ($it)" } ?: ""
        val quitMessage = "* ${sender ?: "Alguien"} ha salido del IRC$reason"
        currentChatTargets.forEach { openTarget ->
            if (openTarget.startsWith("#")) { 
                addMessageToTargetInternal(openTarget, UiChatMessage(quitMessage, UiMessageType.JOIN_PART_QUIT, sender), allMessages)
            }
        }
    }

    data class NickChangeInternalResult(
        val newNickname: String?,
        val newActiveTarget: String?,
        val newChatTargets: List<String>,
        val newUnreadTargets: Set<String>,
        val ownNickChanged: Boolean
    )

    private fun handleNickInternal(
        parsedMessage: ParsedIrcMessage,
        currentNickname: String,
        currentActiveTarget: String?,
        currentChatTargets: List<String>,
        currentUnreadTargets: Set<String>,
        allMessages: MutableMap<String, List<UiChatMessage>> 
    ): NickChangeInternalResult {
        val oldNick = parsedMessage.senderNickname ?: return NickChangeInternalResult(currentNickname, currentActiveTarget, currentChatTargets, currentUnreadTargets, false)
        val newNick = parsedMessage.trailing ?: parsedMessage.params.firstOrNull() ?: return NickChangeInternalResult(currentNickname, currentActiveTarget, currentChatTargets, currentUnreadTargets, false)

        if (oldNick.equals(newNick, ignoreCase = true)) return NickChangeInternalResult(currentNickname, currentActiveTarget, currentChatTargets, currentUnreadTargets, false)

        val nickChangeMsg = UiChatMessage("* $oldNick ahora es conocido como $newNick", UiMessageType.NICK_CHANGE, oldNick)

        var finalNickname = currentNickname
        var finalActiveTarget = currentActiveTarget
        var updatedChatTargetsList = currentChatTargets.toList()
        var updatedUnreadTargetsSet = currentUnreadTargets.toSet()
        var ownNickActuallyChanged = false
        var activeTargetWasOldNick = false

        val currentMessageKeys = allMessages.keys.toList()
        for (target in currentMessageKeys) {
            if (target.equals(oldNick, ignoreCase = true)) { 
                allMessages.remove(target)?.let { messages ->
                    val newMessages = (messages + nickChangeMsg).takeLast(maxUiMessagesPerTarget)
                    allMessages[newNick] = newMessages
                }
                if (finalActiveTarget?.equals(oldNick, ignoreCase = true) == true) {
                    activeTargetWasOldNick = true
                }
            } else { 
                val targetMessages = allMessages[target]
                val oldNickParticipated = targetMessages?.any {
                    it.sender?.equals(oldNick, ignoreCase = true) == true || it.fullText.contains(oldNick, ignoreCase = true)
                } == true

                if (currentChatTargets.any { it.equals(target, ignoreCase = true) } && oldNickParticipated) {
                     allMessages[target]?.let { messages ->
                        allMessages[target] = (messages + nickChangeMsg).takeLast(maxUiMessagesPerTarget)
                    }
                }
            }
        }

        if (updatedChatTargetsList.any { it.equals(oldNick, ignoreCase = true) }) {
            updatedChatTargetsList = updatedChatTargetsList.map { if (it.equals(oldNick, ignoreCase = true)) newNick else it }.distinct()
            if (activeTargetWasOldNick) {
                finalActiveTarget = newNick
            }
        }

        if (updatedUnreadTargetsSet.contains(oldNick)) {
            updatedUnreadTargetsSet = (updatedUnreadTargetsSet - oldNick) + newNick
        }

        if (oldNick.equals(finalNickname, ignoreCase = true)) {
            finalNickname = newNick
            ownNickActuallyChanged = true
        }

        return NickChangeInternalResult(finalNickname, finalActiveTarget, updatedChatTargetsList, updatedUnreadTargetsSet, ownNickActuallyChanged)
    }

    private fun handleModeInternal(
        parsedMessage: ParsedIrcMessage,
        currentNickname: String,
        activeTarget: String?
    ): Pair<String?, UiChatMessage?> {
        val sender = parsedMessage.senderNickname
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        var determinedTargetKey = params.firstOrNull()
        if (determinedTargetKey.isNullOrBlank() || (!determinedTargetKey.startsWith("#") && !determinedTargetKey.equals(currentNickname, ignoreCase = true))) {
            determinedTargetKey = activeTarget ?: SERVER_TARGET_ID
        }
        val by = sender ?: parsedMessage.prefix ?: "Server"
        val modes = params.drop(1).joinToString(" ") + (trailing?.let { " :$it" } ?: "")
        val newUiMsg = UiChatMessage("* $by establece modo $modes en $determinedTargetKey", UiMessageType.MODE_CHANGE, by)
        return Pair(determinedTargetKey, newUiMsg)
    }

    private fun handleNumericReplyInternal(
        parsedMessage: ParsedIrcMessage,
        currentNickname: String
    ): Triple<String?, UiChatMessage?, String?> { 
        val command = parsedMessage.command
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing
        var newNickname: String? = null

        val targetKey = SERVER_TARGET_ID 
        var content = trailing ?: params.joinToString(" ")
        if (command == "001") { 
            content = trailing ?: params.drop(1).joinToString(" ") 
            val confirmedNick = params.firstOrNull()
            if (confirmedNick != null && !confirmedNick.equals(currentNickname, ignoreCase = true)) {
                newNickname = confirmedNick
            }
        }
        val uiMsg = UiChatMessage("[INFO] $content", UiMessageType.SERVER_INFO, parsedMessage.prefix ?: "Server")
        return Triple(targetKey, uiMsg, newNickname)
    }

    private fun handleErrorReplyInternal(
        parsedMessage: ParsedIrcMessage,
        activeTarget: String?
    ): Pair<String?, UiChatMessage?> {
        val command = parsedMessage.command
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        val targetKey = activeTarget ?: SERVER_TARGET_ID 
        val errorParams = params.joinToString(" ")
        val errorTrailing = trailing ?: ""
        val errorMessage = "Error $command: $errorParams $errorTrailing"
        val uiMsg = UiChatMessage(errorMessage, UiMessageType.SERVER_INFO, parsedMessage.prefix ?: "Server")
        return Pair(targetKey, uiMsg)
    }

    private fun handleOtherCommandInternal(
        parsedMessage: ParsedIrcMessage,
        activeTarget: String?
    ): Pair<String?, UiChatMessage?> {
        val command = parsedMessage.command
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing
        val prefix = parsedMessage.prefix

        val targetKey = activeTarget ?: SERVER_TARGET_ID
        val fullOriginalText = "${prefix?.let { ":$it " } ?: ""}$command ${params.joinToString(" ")}${trailing?.let { " :$it" } ?: ""}"
        val uiMsg = UiChatMessage("[${command.uppercase()}] $fullOriginalText", UiMessageType.OTHER_COMMAND, prefix)
        return Pair(targetKey, uiMsg)
    }
}
