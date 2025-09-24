package com.jfaf.irc.ui.viewmodels

import android.util.Log
import androidx.compose.ui.text.* // Importación global para androidx.compose.ui.text
import androidx.compose.ui.text.font.FontWeight
import com.jfaf.irc.data.model.ParsedIrcMessage
import javax.inject.Inject

// Data classes used by IrcMessageHandler
data class ChatUiSnapshot(
    val currentNickname: String,
    val activeTarget: String?,
    val allMessages: Map<String, List<UiChatMessage>>,
    val chatTargets: List<String>,
    val unreadTargets: Set<String>,
    val usersInChannel: Map<String, List<String>> = emptyMap()
)

data class ChatUpdateResult(
    val newCurrentNickname: String,
    val newActiveTarget: String?,
    val newAllMessages: Map<String, List<UiChatMessage>>,
    val newChatTargets: List<String>,
    val newUnreadTargets: Set<String>,
    val newUsersInChannel: Map<String, List<String>> = emptyMap(),
    val uiMessageToAdd: UiChatMessage? = null,
    val targetForUiMessage: String? = null,
    val privateMessageEventNick: String? = null,
    val ownNickChangedTo: String? = null
)

// Nueva clase de datos para JOIN y PART
data class ChannelEventResult(
    val targetForUiMessage: String?,
    val uiMessageToAdd: UiChatMessage?,
    val newActiveTarget: String?, 
    val newChatTargets: List<String>?, 
    val newUnreadTargets: Set<String>?,
    val updatedUsersInChannel: Map<String, List<String>> 
)

// Clase de datos actualizada para NICK
data class NickChangeInternalResult(
    val newNickname: String?,
    val newActiveTarget: String?,
    val newChatTargets: List<String>,
    val newUnreadTargets: Set<String>,
    val ownNickChanged: Boolean,
    val updatedUsersInChannel: Map<String, List<String>> // Campo añadido
)

// Mantenemos FiveTuple por si se usa en otro lugar o para PRIVMSG/NOTICE si no necesitan usersInChannel
data class FiveTuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

class IrcMessageHandler @Inject constructor() {

    private val SERVER_TARGET_ID = "Servidor"
    private val maxUiMessagesPerTarget = 150

    private fun extractImageUrl(text: String): String? {
        Log.d("extractImageUrl", "Input text: '$text'")
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
        Log.d("IrcMessageHandler.AddMsg", "Target: '$target', Msg: '${message.fullText}', Annotated: '${message.annotatedString}', Img: ${message.imageUrl}, List size before: ${currentMessages[target]?.size ?: 0}")
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
        var usersInChannel = snapshot.usersInChannel.toMutableMap()

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
                val result = handleJoinInternal(parsedMessage, currentNickname, chatTargets, unreadTargets, usersInChannel)
                targetForUiMessage = result.targetForUiMessage
                uiMessageToAdd = result.uiMessageToAdd
                activeTarget = result.newActiveTarget ?: activeTarget
                result.newChatTargets?.let { chatTargets = it.toMutableList() }
                result.newUnreadTargets?.let { unreadTargets = it.toMutableSet() }
                usersInChannel = result.updatedUsersInChannel.toMutableMap()
            }
            "PART" -> {
                val result = handlePartInternal(parsedMessage, currentNickname, activeTarget, chatTargets, unreadTargets, usersInChannel)
                targetForUiMessage = result.targetForUiMessage
                uiMessageToAdd = result.uiMessageToAdd
                activeTarget = result.newActiveTarget ?: activeTarget
                result.newChatTargets?.let { chatTargets = it.toMutableList() }
                result.newUnreadTargets?.let { unreadTargets = it.toMutableSet() }
                usersInChannel = result.updatedUsersInChannel.toMutableMap()
            }
            "QUIT" -> {
                usersInChannel = handleQuitInternal(parsedMessage, chatTargets, allMessages, usersInChannel).toMutableMap()
            }
            "NICK" -> {
                val nickChangeResult = handleNickInternal(parsedMessage, currentNickname, activeTarget, chatTargets, unreadTargets, allMessages, usersInChannel)
                currentNickname = nickChangeResult.newNickname ?: currentNickname
                activeTarget = nickChangeResult.newActiveTarget ?: activeTarget
                chatTargets = nickChangeResult.newChatTargets.toMutableList()
                unreadTargets = nickChangeResult.newUnreadTargets.toMutableSet()
                usersInChannel = nickChangeResult.updatedUsersInChannel.toMutableMap()
                if (nickChangeResult.ownNickChanged) {
                    ownNickChangedTo = currentNickname
                }
            }
            "MODE" -> {
                val result = handleModeInternal(parsedMessage, currentNickname, activeTarget)
                targetForUiMessage = result.first
                uiMessageToAdd = result.second
            }
            "353" -> { // RPL_NAMREPLY
                val result = handleNamesReplyInternal(parsedMessage, usersInChannel)
                usersInChannel = result.third.toMutableMap()
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
        
        return ChatUpdateResult(
            newCurrentNickname = currentNickname,
            newActiveTarget = activeTarget,
            newAllMessages = allMessages.toMap(),
            newChatTargets = ensureServerTargetIsFirst(chatTargets.toList()),
            newUnreadTargets = unreadTargets.toSet(),
            newUsersInChannel = usersInChannel.toMap(),
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

        val rawMessageTextForFullText = when {
            isToChannel -> "<${sender}> $content"
            currentIsOwn -> "<${currentNickname}> $content" 
            else -> "<${sender}> $content"
        }
        
        val parsedContent = MircColorParser.parse(content)
        val senderDisplay = sender ?: currentNickname // Fallback for sender display
        val finalAnnotatedString = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append("<$senderDisplay> ")
            }
            append(parsedContent)
        }

        val imageUrl = extractImageUrl(content) // Extract from raw content
        val newUiMsg = UiChatMessage(
            fullText = rawMessageTextForFullText, 
            annotatedString = finalAnnotatedString,
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
        
        val rawMessageTextForFullText = "-$from- $content"
        val parsedContent = MircColorParser.parse(content)
        val finalAnnotatedString = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) { 
                append("-$from- ")
            }
            append(parsedContent)
        }

        val imageUrl = extractImageUrl(content)
        val newUiMsg = UiChatMessage(
            fullText = rawMessageTextForFullText, 
            annotatedString = finalAnnotatedString,
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
        currentUnreadTargets: Set<String>,
        currentUsersInChannel: Map<String, List<String>>
    ): ChannelEventResult { 
        val userJoining = parsedMessage.senderNickname
        val params = parsedMessage.params
        val channel = parsedMessage.trailing ?: params.firstOrNull()

        var newActiveTarget: String? = null
        var updatedChatTargets: List<String>? = null
        var updatedUnreadTargets: Set<String>? = null
        val mutableUsersMap = currentUsersInChannel.toMutableMap()

        if (channel == null || userJoining == null) {
            return ChannelEventResult(null, null, null, null, null, currentUsersInChannel)
        }

        val currentChannelUsers = mutableUsersMap[channel]?.toMutableList() ?: mutableListOf()
        if (!currentChannelUsers.any { it.equals(userJoining, ignoreCase = true) }) {
            currentChannelUsers.add(userJoining)
            mutableUsersMap[channel] = currentChannelUsers.distinctBy { it.lowercase() }.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

        if (userJoining.equals(currentNickname, ignoreCase = true)) {
            if (!currentChatTargets.any { it.equals(channel, ignoreCase = true) }) {
                updatedChatTargets = currentChatTargets + channel
            }
            newActiveTarget = channel
            if (currentUnreadTargets.contains(channel)) {
                updatedUnreadTargets = currentUnreadTargets - channel
            }
        }
        val messageText = "* $userJoining ha entrado a $channel"
        val newUiMsg = UiChatMessage(messageText, annotatedString = AnnotatedString(messageText), UiMessageType.JOIN_PART_QUIT, userJoining)
        return ChannelEventResult(channel, newUiMsg, newActiveTarget, updatedChatTargets, updatedUnreadTargets, mutableUsersMap.toMap())
    }

    private fun handlePartInternal(
        parsedMessage: ParsedIrcMessage,
        currentNickname: String,
        currentActiveTarget: String?,
        currentChatTargets: List<String>,
        currentUnreadTargets: Set<String>,
        currentUsersInChannel: Map<String, List<String>>
    ): ChannelEventResult { 
        val userParting = parsedMessage.senderNickname
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing
        var channelName = params.firstOrNull()
        if (channelName.isNullOrBlank() && !trailing.isNullOrBlank() && trailing.startsWith("#")) {
            channelName = trailing.split(" ")[0]
        }
        
        var newActiveTarget: String? = null
        var updatedChatTargets: List<String>? = null
        var updatedUnreadTargets: Set<String>? = null
        val mutableUsersMap = currentUsersInChannel.toMutableMap()

        if (channelName.isNullOrBlank() || userParting == null) {
            return ChannelEventResult(null, null, null, null, null, currentUsersInChannel)
        }
        
        val currentChannelUsers = mutableUsersMap[channelName]?.toMutableList()
        if (currentChannelUsers != null) {
            if (currentChannelUsers.removeIf { it.equals(userParting, ignoreCase = false) }) {
                mutableUsersMap[channelName] = currentChannelUsers.sortedWith(String.CASE_INSENSITIVE_ORDER)
            }
        }

        val reasonPart = if (trailing != channelName) trailing?.substringAfter(channelName)?.trim() else null
        val reasonMsgContent = reasonPart?.let { if (it.startsWith(":")) it.substring(1) else it } ?: ""
        val reasonMsg = if (reasonMsgContent.isNotBlank()) " ($reasonMsgContent)" else ""
        val messageText = "* $userParting ha salido de $channelName$reasonMsg"
        val newUiMsg = UiChatMessage(messageText, annotatedString = AnnotatedString(messageText), UiMessageType.JOIN_PART_QUIT, userParting)

        if (userParting.equals(currentNickname, ignoreCase = true)) {
            if (currentChatTargets.any { it.equals(channelName, ignoreCase = true) }) {
                updatedChatTargets = currentChatTargets.filterNot { it.equals(channelName, ignoreCase = true) }
                if (currentUnreadTargets.contains(channelName)) {
                    updatedUnreadTargets = currentUnreadTargets - channelName
                }
                if (currentActiveTarget?.equals(channelName, ignoreCase = true) == true) {
                    newActiveTarget = updatedChatTargets.firstOrNull() ?: SERVER_TARGET_ID
                }
            }
        }
        return ChannelEventResult(channelName, newUiMsg, newActiveTarget, updatedChatTargets, updatedUnreadTargets, mutableUsersMap.toMap())
    }

    private fun handleQuitInternal(
        parsedMessage: ParsedIrcMessage,
        currentChatTargets: List<String>,
        allMessages: MutableMap<String, List<UiChatMessage>>,
        currentUsersInChannel: Map<String, List<String>>
    ): Map<String, List<String>> { 
        val userQuitting = parsedMessage.senderNickname
        val trailing = parsedMessage.trailing
        val reason = trailing?.let { " ($it)" } ?: ""
        val quitMessageText = "* ${userQuitting ?: "Alguien"} ha salido del IRC$reason"
        val quitMessage = UiChatMessage(quitMessageText, annotatedString = AnnotatedString(quitMessageText), UiMessageType.JOIN_PART_QUIT, userQuitting)
        
        val mutableUsersMap = currentUsersInChannel.toMutableMap()
        if (userQuitting != null) {
            currentUsersInChannel.keys.forEach { channel ->
                val channelUsers = mutableUsersMap[channel]?.toMutableList()
                if (channelUsers != null) {
                    if (channelUsers.removeIf { it.equals(userQuitting, ignoreCase = false) }) {
                        mutableUsersMap[channel] = channelUsers.sortedWith(String.CASE_INSENSITIVE_ORDER)
                    }
                }
            }
        }
        currentChatTargets.forEach { openTarget ->
            if (openTarget.startsWith("#")) { 
                addMessageToTargetInternal(openTarget, quitMessage, allMessages)
            }
        }
        return mutableUsersMap.toMap()
    }

    private fun handleNickInternal(
        parsedMessage: ParsedIrcMessage,
        currentNickname: String,
        currentActiveTarget: String?,
        currentChatTargets: List<String>,
        currentUnreadTargets: Set<String>,
        allMessages: MutableMap<String, List<UiChatMessage>>,
        currentUsersInChannel: Map<String, List<String>>
    ): NickChangeInternalResult {
        val oldNick = parsedMessage.senderNickname
        val newNick = parsedMessage.trailing ?: parsedMessage.params.firstOrNull()

        var finalNickname = currentNickname
        var finalActiveTarget = currentActiveTarget
        var updatedChatTargetsList = currentChatTargets.toList()
        var updatedUnreadTargetsSet = currentUnreadTargets.toSet()
        var ownNickActuallyChanged = false
        var activeTargetWasOldNick = false
        val mutableUsersMap = currentUsersInChannel.toMutableMap()

        if (oldNick == null || newNick == null || oldNick.equals(newNick, ignoreCase = true)) {
            return NickChangeInternalResult(currentNickname, currentActiveTarget, currentChatTargets, currentUnreadTargets, false, currentUsersInChannel)
        }

        currentUsersInChannel.keys.forEach { channel ->
            val channelUsers = mutableUsersMap[channel]?.toMutableList()
            if (channelUsers != null && channelUsers.any { it.equals(oldNick, ignoreCase = false) }) {
                channelUsers.removeIf { it.equals(oldNick, ignoreCase = false) }
                channelUsers.add(newNick)
                mutableUsersMap[channel] = channelUsers.distinctBy { it.lowercase() }.sortedWith(String.CASE_INSENSITIVE_ORDER)
            }
        }

        val messageText = "* $oldNick ahora es conocido como $newNick"
        val nickChangeMsg = UiChatMessage(messageText, annotatedString = AnnotatedString(messageText), UiMessageType.NICK_CHANGE, oldNick)
        
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
                    it.sender?.equals(oldNick, ignoreCase = true) == true || 
                    (it.annotatedString?.text ?: it.fullText).contains(oldNick, ignoreCase = true)
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

        return NickChangeInternalResult(finalNickname, finalActiveTarget, updatedChatTargetsList, updatedUnreadTargetsSet, ownNickActuallyChanged, mutableUsersMap.toMap())
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
        val messageText = "* $by establece modo $modes en $determinedTargetKey"
        val newUiMsg = UiChatMessage(messageText, annotatedString = AnnotatedString(messageText), UiMessageType.MODE_CHANGE, by)
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
        val messageText = "[INFO] $content"
        val annotatedContent = MircColorParser.parse(content)
        val finalAnnotatedString = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Light)) {
                append("[INFO] ")
            }
            append(annotatedContent)
        }
        val uiMsg = UiChatMessage(messageText, annotatedString = finalAnnotatedString, UiMessageType.SERVER_INFO, parsedMessage.prefix ?: "Server")
        return Triple(targetKey, uiMsg, newNickname)
    }

    private fun handleErrorReplyInternal(
        parsedMessage: ParsedIrcMessage,
        activeTarget: String?
    ): Pair<String?, UiChatMessage?> {
        val command = parsedMessage.command
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        var targetKey: String
        var errorMessageText: String
        var annotatedContentText: String
        var errorPrefix: String

        if (command == "401") { // ERR_NOSUCHNICK
            targetKey = SERVER_TARGET_ID
            val nickWithError = params.getOrNull(1) ?: "Usuario desconocido"
            errorMessageText = "[WHOIS Error] Usuario '$nickWithError' no encontrado."
            annotatedContentText = "Usuario '$nickWithError' no encontrado."
            errorPrefix = "[WHOIS Error] "
        } else {
            targetKey = activeTarget ?: SERVER_TARGET_ID
            val errorParams = params.joinToString(" ")
            val errorTrailing = trailing ?: ""
            errorMessageText = "Error $command: $errorParams $errorTrailing"
            annotatedContentText = "$errorParams $errorTrailing"
            errorPrefix = "Error $command: "
        }

        val annotatedContent = MircColorParser.parse(annotatedContentText)
        val finalAnnotatedString = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(errorPrefix)
            }
            append(annotatedContent)
        }
        val uiMsg = UiChatMessage(errorMessageText, annotatedString = finalAnnotatedString, UiMessageType.SERVER_INFO, parsedMessage.prefix ?: "Server")
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
        val uiMsg = UiChatMessage(
            fullText = "[${command.uppercase()}] $fullOriginalText", 
            annotatedString = AnnotatedString("[${command.uppercase()}] $fullOriginalText"), 
            type = UiMessageType.OTHER_COMMAND, 
            sender = prefix
        )
        return Pair(targetKey, uiMsg)
    }

    private fun handleNamesReplyInternal(
        parsedMessage: ParsedIrcMessage,
        currentUsersInChannel: Map<String, List<String>>
    ): Triple<String?, UiChatMessage?, Map<String, List<String>>> {
        if (parsedMessage.params.size < 3) {
            return Triple(null, null, currentUsersInChannel)
        }
        val channel = parsedMessage.params[2]
        val namesString = parsedMessage.trailing

        if (channel.isBlank() || namesString.isNullOrBlank()) {
            return Triple(null, null, currentUsersInChannel)
        }

        val namesList = namesString.trim().split(" ")
            .map { nickWithPrefix ->
                var cleanNick = nickWithPrefix
                if (cleanNick.startsWith("@") || cleanNick.startsWith("+") || cleanNick.startsWith("%") || cleanNick.startsWith("&") || cleanNick.startsWith("~")) {
                }
                cleanNick
            }
            .filter { it.isNotBlank() }

        val updatedUsersMap = currentUsersInChannel.toMutableMap()
        val existingNames = updatedUsersMap[channel]?.toMutableList() ?: mutableListOf()
        
        namesList.forEach { newName ->
            if (!existingNames.any { it.equals(newName, ignoreCase = true) }) {
                existingNames.add(newName)
            }
        }
        updatedUsersMap[channel] = existingNames.distinctBy { it.lowercase() }
            .sortedWith(compareBy<String> {
                when {
                    it.startsWith("@") -> 0 
                    it.startsWith("+") -> 1 
                    else -> 2 
                }
            }.thenBy { it.lowercase() })
        
        return Triple(null, null, updatedUsersMap.toMap())
    }
}
