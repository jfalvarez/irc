package com.jfaf.irc.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.data.repositories.IrcRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Data classes and Enums for UI Messages ---
data class UiChatMessage(
    val fullText: String,
    val type: UiMessageType,
    val sender: String? = null,
    val isOwnMessage: Boolean = false
)

enum class UiMessageType {
    CHANNEL_MSG_RECEIVED,   // PRIVMSG a un canal, de otro usuario
    CHANNEL_MSG_SENT,       // PRIVMSG a un canal, del usuario actual (eco o local)
    PRIVATE_MSG_RECEIVED,   // PRIVMSG a mi nick, de otro usuario
    PRIVATE_MSG_SENT,       // PRIVMSG a otro nick, del usuario actual (eco o local)
    JOIN_PART_QUIT,
    NICK_CHANGE,
    MODE_CHANGE,
    NOTICE,
    SERVER_INFO,            // Mensajes generales del servidor
    SYSTEM_MESSAGE,         // Mensajes generados por el cliente para el usuario
    OTHER_COMMAND,
    UNKNOWN
}
// --- End of Data classes ---

private const val SERVER_TARGET_ID = "Servidor"

@HiltViewModel
class MainViewModel @Inject constructor(
    private val ircRepository: IrcRepository
) : ViewModel() {

    val connectionState: StateFlow<Boolean> = ircRepository.connectionState
    val rawIrcMessagesEvents: SharedFlow<ParsedIrcMessage> = ircRepository.incomingMessages

    var currentNickname = "IrcUser${(100..999).random()}"
        private set
    private val defaultHost = "irc.irc-hispano.org"
    private val maxUiMessagesPerTarget = 150

    private val _incomingPrivateMessageEvent = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val incomingPrivateMessageEvent: SharedFlow<String> = _incomingPrivateMessageEvent.asSharedFlow()

    private val _userMessageEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val userMessageEvents: SharedFlow<String> = _userMessageEvents.asSharedFlow()

    private val _unreadTargets = MutableStateFlow<Set<String>>(emptySet())
    val unreadTargets: StateFlow<Set<String>> = _unreadTargets.asStateFlow()

    private val emoticonToEmojiMap = mapOf(
        ":)" to "😊", ":-)" to "😊", ":D" to "😀", ":-D" to "😀",
        ";)" to "😉", ":-)" to "😉", ":(" to "😞", ":-(" to "😞",
        ":P" to "😛", ":-P" to "😛", "xD" to "😆", "XD" to "😆",
        ":O" to "😮", ":-O" to "😮", "<3" to "❤️", ":*" to "😘",
        ":-|" to "😐", ":/" to "😕", ":-\\" to "😕", "B)" to "😎", "B-)" to "😎"
    )

    private fun replaceEmoticonsWithEmoji(text: String): String {
        var processedText = text
        val sortedEmoticons = emoticonToEmojiMap.keys.sortedByDescending { it.length }
        for (emoticon in sortedEmoticons) {
            processedText = processedText.replace(emoticon, emoticonToEmojiMap[emoticon]!!)
        }
        return processedText
    }

    private val _chatTargets = MutableStateFlow<List<String>>(emptyList())
    val chatTargets: StateFlow<List<String>> = _chatTargets.asStateFlow()

    private val _activeTarget = MutableStateFlow<String?>(null)
    val activeTarget: StateFlow<String?> = _activeTarget.asStateFlow()

    private val _allMessages = MutableStateFlow<Map<String, List<UiChatMessage>>>(emptyMap())

    val uiMessages: StateFlow<List<UiChatMessage>> = combine(
        _activeTarget, _allMessages
    ) { active, allMsgs ->
        allMsgs[active] ?: emptyList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    private fun ensureServerTargetIsFirst(targets: List<String>): List<String> {
        // 1. Obtener todos los targets únicos, excluyendo SERVER_TARGET_ID temporalmente.
        val otherTargets = targets.asSequence().filterNot { it == SERVER_TARGET_ID }.distinct().toList()
        // 2. Crear una nueva lista comenzando con SERVER_TARGET_ID, seguida por los otros targets únicos.
        return listOf(SERVER_TARGET_ID) + otherTargets
    }

    init {
        rawIrcMessagesEvents
            .onEach { parsedMessage ->
                Log.d("MainViewModel", "Internal Raw In (from Service via Repo): $parsedMessage")
                processIncomingParsedMessage(parsedMessage)
            }
            .launchIn(viewModelScope)

        connectionState.onEach { isConnected ->
            Log.i("MainViewModel", "Estado de conexión (desde Servicio): ${if (isConnected) "CONECTADO" else "DESCONECTADO"}")
            if (!isConnected) {
                handleServiceDisconnected()
            } else {
                handleServiceConnected()
            }
        }.launchIn(viewModelScope)
    }

    private fun handleServiceConnected() {
        val serverMessages = _allMessages.value[SERVER_TARGET_ID] ?: emptyList()
        val lastMessageText = serverMessages.lastOrNull()?.fullText ?: ""

        if (_chatTargets.value.isEmpty() || _activeTarget.value == null || !lastMessageText.contains("Conectado al servidor", ignoreCase = true)) {
            _chatTargets.value = ensureServerTargetIsFirst((_chatTargets.value + SERVER_TARGET_ID).distinct())
            _activeTarget.value = _activeTarget.value ?: SERVER_TARGET_ID
            val updatedMessages = (serverMessages + UiChatMessage("Conectado al servidor.", UiMessageType.SYSTEM_MESSAGE)).takeLast(maxUiMessagesPerTarget)
            _allMessages.value = _allMessages.value + (SERVER_TARGET_ID to updatedMessages)
        }
        Log.i("MainViewModel", "Servicio conectado.")
    }

    private fun handleServiceDisconnected() {
        val serverMessages = _allMessages.value[SERVER_TARGET_ID] ?: emptyList()
        val lastMessageText = serverMessages.lastOrNull()?.fullText ?: ""
        val disconnectMessages = listOf("Desconectado", "Conexión perdida", "El servicio IRC ya no está activo", "Servicio detenido")
        val alreadyHasDisconnectMsg = disconnectMessages.any { lastMessageText.contains(it, ignoreCase = true) }

        val messageText = when {
            lastMessageText.contains("Servicio detenido", ignoreCase = true) -> "Desconectado. El servicio fue detenido."
            alreadyHasDisconnectMsg -> null
            else -> "Desconectado. El servicio IRC perdió la conexión o fue detenido."
        }

        val updatedServerMessages = if (messageText != null) {
            (serverMessages + UiChatMessage(messageText, UiMessageType.SYSTEM_MESSAGE)).takeLast(maxUiMessagesPerTarget)
        } else {
            serverMessages
        }

        _chatTargets.value = ensureServerTargetIsFirst(listOf(SERVER_TARGET_ID))
        _activeTarget.value = SERVER_TARGET_ID
        _allMessages.value = mapOf(SERVER_TARGET_ID to updatedServerMessages)
        _unreadTargets.value = emptySet()
        Log.i("MainViewModel", "UI actualizada para reflejar desconexión del servicio. Mensaje añadido: $messageText")
    }

    private fun addMessageToTarget(target: String, message: UiChatMessage) {
        val currentMessagesForTarget = _allMessages.value[target] ?: emptyList()
        val updatedMessagesForTarget = (currentMessagesForTarget + message).takeLast(maxUiMessagesPerTarget)
        _allMessages.value = _allMessages.value + (target to updatedMessagesForTarget)
    }

    // ------------ START OF REFACTORED IRC COMMAND HANDLERS ------------
    private fun handlePrivmsg(parsedMessage: ParsedIrcMessage): Pair<String?, UiChatMessage?> {
        val sender = parsedMessage.senderNickname
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        val msgTarget = params.firstOrNull() ?: return Pair(null, null)
        var content = trailing ?: ""
        content = replaceEmoticonsWithEmoji(content)
        val isToChannel = msgTarget.startsWith("#")
        val currentIsOwn = sender?.equals(currentNickname, ignoreCase = true) == true
        val determinedTargetKey = if (isToChannel) msgTarget else if (currentIsOwn) msgTarget else sender

        if (determinedTargetKey != null) {
            if (!currentIsOwn) { // Message is from someone else
                if (!determinedTargetKey.equals(_activeTarget.value, ignoreCase = true)) {
                    _unreadTargets.value = _unreadTargets.value + determinedTargetKey
                    Log.d("MainViewModel", "Target '$determinedTargetKey' marked as unread.")
                }
                if (!isToChannel) {
                    _incomingPrivateMessageEvent.tryEmit(determinedTargetKey)
                    Log.d("MainViewModel", "Incoming PM from '$sender' for target '$determinedTargetKey'. Event emitted.")
                }
            }

            if (determinedTargetKey != SERVER_TARGET_ID && !isToChannel && !currentIsOwn) {
                if (!_chatTargets.value.any { it.equals(determinedTargetKey, ignoreCase = true) }) {
                    Log.d("MainViewModel", "PRIVMSG: Adding new PM target '$determinedTargetKey' to _chatTargets.")
                    _chatTargets.value = ensureServerTargetIsFirst((_chatTargets.value + determinedTargetKey).distinct())
                    if (_allMessages.value[determinedTargetKey] == null) {
                        _allMessages.value = _allMessages.value + (determinedTargetKey to emptyList())
                    }
                }
            }
        }
        val messageText = when { isToChannel -> "<${sender}> $content"; currentIsOwn -> "<${currentNickname}> $content"; else -> "<${sender}> $content" }
        val newUiMsg = UiChatMessage(fullText = messageText, type = when { currentIsOwn && isToChannel -> UiMessageType.CHANNEL_MSG_SENT; currentIsOwn && !isToChannel -> UiMessageType.PRIVATE_MSG_SENT; !currentIsOwn && isToChannel -> UiMessageType.CHANNEL_MSG_RECEIVED; else -> UiMessageType.PRIVATE_MSG_RECEIVED }, sender = sender, isOwnMessage = currentIsOwn)
        return Pair(determinedTargetKey, newUiMsg)
    }

    private fun handleNotice(parsedMessage: ParsedIrcMessage): Pair<String?, UiChatMessage?> {
        val sender = parsedMessage.senderNickname
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        val noticeTargetParam = params.firstOrNull()
        val from = sender ?: parsedMessage.prefix ?: "Server"
        var content = trailing ?: params.joinToString(" "); content = replaceEmoticonsWithEmoji(content)
        val determinedTargetKey = if (noticeTargetParam?.equals(currentNickname, ignoreCase = true) == true && sender != null) sender else _activeTarget.value ?: SERVER_TARGET_ID
        val newUiMsg = UiChatMessage("-$from- $content", UiMessageType.NOTICE, from)

        if (determinedTargetKey != null && sender != null && noticeTargetParam?.equals(currentNickname, ignoreCase = true) == true && !determinedTargetKey.equals(_activeTarget.value, ignoreCase = true)) {
            _unreadTargets.value = _unreadTargets.value + determinedTargetKey
            Log.d("MainViewModel", "NOTICE Target '$determinedTargetKey' marked as unread.")
        }
        return Pair(determinedTargetKey, newUiMsg)
    }

    private fun handleJoin(parsedMessage: ParsedIrcMessage): Pair<String?, UiChatMessage?> {
        val sender = parsedMessage.senderNickname
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        val channel = trailing ?: params.firstOrNull() ?: return Pair(null, null)
        if (sender?.equals(currentNickname, ignoreCase = true) == true) {
            if (!_chatTargets.value.any { it.equals(channel, ignoreCase = true) }) {
                Log.d("MainViewModel", "JOIN: Adding target '$channel' to _chatTargets.")
                _chatTargets.value = ensureServerTargetIsFirst((_chatTargets.value + channel).distinct())
            }
            _activeTarget.value = channel
            if (_unreadTargets.value.contains(channel)) {
                _unreadTargets.value = _unreadTargets.value - channel
            }
        }
        val newUiMsg = UiChatMessage("* ${sender ?: "Alguien"} ha entrado a $channel", UiMessageType.JOIN_PART_QUIT, sender)
        return Pair(channel, newUiMsg)
    }

    private fun handlePart(parsedMessage: ParsedIrcMessage): Pair<String?, UiChatMessage?> {
        val sender = parsedMessage.senderNickname
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        var channelName = params.firstOrNull()
        if (channelName.isNullOrBlank() && !trailing.isNullOrBlank() && trailing.startsWith("#")) {
            channelName = trailing.split(" ")[0]
        }
        if (channelName.isNullOrBlank()) {
            Log.w("MainViewModel", "PART sin nombre de canal. Params: $params, Trailing: $trailing")
            return Pair(null, null)
        }

        val reasonPart = if (trailing != channelName) trailing?.substringAfter(channelName)?.trim() else null
        var reasonMsgContent = reasonPart?.let { if (it.startsWith(":")) it.substring(1) else it } ?: ""
        reasonMsgContent = replaceEmoticonsWithEmoji(reasonMsgContent)
        val reasonMsg = if (reasonMsgContent.isNotBlank()) " ($reasonMsgContent)" else ""
        val newUiMsg = UiChatMessage("* ${sender ?: "Alguien"} ha salido de $channelName$reasonMsg", UiMessageType.JOIN_PART_QUIT, sender)

        if (sender?.equals(currentNickname, ignoreCase = true) == true) {
            if (_chatTargets.value.any { it.equals(channelName, ignoreCase = true) }) {
                _chatTargets.value = ensureServerTargetIsFirst(_chatTargets.value.filterNot { it.equals(channelName, ignoreCase = true) })
                if (_unreadTargets.value.contains(channelName)) {
                    _unreadTargets.value = _unreadTargets.value - channelName
                }
                if (_activeTarget.value?.equals(channelName, ignoreCase = true) == true) {
                    _activeTarget.value = _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
                }
            }
        }
        return Pair(channelName, newUiMsg)
    }

    private fun handleQuit(parsedMessage: ParsedIrcMessage) { // Returns nothing, handles messages internally
        val sender = parsedMessage.senderNickname
        val trailing = parsedMessage.trailing
        var reason = trailing?.let { " ($it)" } ?: ""; reason = replaceEmoticonsWithEmoji(reason)
        val quitMessage = "* ${sender ?: "Alguien"} ha salido del IRC$reason"
        ArrayList(_chatTargets.value).forEach { openTarget ->
            if (openTarget.startsWith("#")) { // Add quit message to all open channels
                addMessageToTarget(openTarget, UiChatMessage(quitMessage, UiMessageType.JOIN_PART_QUIT, sender))
            }
        }
        // No specific targetKey/uiMsg for the main log line, QUIT applies broadly.
    }

    // --- Start of NICK handling refactor ---
    private fun updateMessagesForNickChange(oldNick: String, newNick: String, nickChangeMsg: UiChatMessage): Boolean {
        val updatedAllMessages = _allMessages.value.toMutableMap()
        var activeTargetPotentiallyChanged = false

        val currentMessageKeys = _allMessages.value.keys.toList()

        for (target in currentMessageKeys) {
            if (target.equals(oldNick, ignoreCase = true)) { // Es una ventana de PM con el oldNick
                updatedAllMessages.remove(target)?.let { messages ->
                    val newMessages = (messages + nickChangeMsg).takeLast(maxUiMessagesPerTarget)
                    updatedAllMessages[newNick] = newMessages
                }
                if (_activeTarget.value?.equals(oldNick, ignoreCase = true) == true) {
                    activeTargetPotentiallyChanged = true
                }
            } else { // Es un canal u otra ventana de PM donde el usuario podría haber hablado o sido mencionado
                val targetMessages = _allMessages.value[target]
                val oldNickParticipated = targetMessages?.any {
                    it.sender?.equals(oldNick, ignoreCase = true) == true || it.fullText.contains(oldNick, ignoreCase = true)
                } == true

                if (_chatTargets.value.any { it.equals(target, ignoreCase = true) } && oldNickParticipated) {
                     updatedAllMessages[target]?.let { messages ->
                        updatedAllMessages[target] = (messages + nickChangeMsg).takeLast(maxUiMessagesPerTarget)
                    }
                }
            }
        }
        _allMessages.value = updatedAllMessages
        return activeTargetPotentiallyChanged
    }

    private fun updateChatStateForNickChange(oldNick: String, newNick: String, activeTargetWasOldNick: Boolean) {
        if (_chatTargets.value.any { it.equals(oldNick, ignoreCase = true) }) {
            _chatTargets.value = ensureServerTargetIsFirst(
                _chatTargets.value.map { if (it.equals(oldNick, ignoreCase = true)) newNick else it }.distinct()
            )
            if (activeTargetWasOldNick) {
                _activeTarget.value = newNick
            }
        }

        if (_unreadTargets.value.contains(oldNick)) {
            _unreadTargets.value = (_unreadTargets.value - oldNick) + newNick
        }
    }

    private fun handleNick(parsedMessage: ParsedIrcMessage) { // Returns nothing, handles messages and state internally
        val oldNick = parsedMessage.senderNickname ?: return
        val newNick = parsedMessage.trailing ?: parsedMessage.params.firstOrNull() ?: return

        if (oldNick.equals(newNick, ignoreCase = true)) return

        val nickChangeMsg = UiChatMessage("* $oldNick ahora es conocido como $newNick", UiMessageType.NICK_CHANGE, oldNick)

        val activeTargetWasOldNick = updateMessagesForNickChange(oldNick, newNick, nickChangeMsg)
        updateChatStateForNickChange(oldNick, newNick, activeTargetWasOldNick)

        if (oldNick.equals(this.currentNickname, ignoreCase = true)) {
            this.currentNickname = newNick
            Log.d("MainViewModel", "currentNickname actualizado a: $newNick por NICK.")
        }
        // No specific targetKey/uiMsg for the main log line, NICK applies broadly or is logged per target.
    }
    // --- End of NICK handling refactor ---

    private fun handleMode(parsedMessage: ParsedIrcMessage): Pair<String?, UiChatMessage?> {
        val sender = parsedMessage.senderNickname
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        var determinedTargetKey = params.firstOrNull()
        if (determinedTargetKey.isNullOrBlank() || (!determinedTargetKey.startsWith("#") && !determinedTargetKey.equals(currentNickname, ignoreCase = true))) {
            determinedTargetKey = _activeTarget.value ?: SERVER_TARGET_ID
        }
        val by = sender ?: parsedMessage.prefix ?: "Server"
        var modes = params.drop(1).joinToString(" ") + (trailing?.let { " :$it" } ?: "")
        modes = replaceEmoticonsWithEmoji(modes)
        val newUiMsg = UiChatMessage("* $by establece modo $modes en $determinedTargetKey", UiMessageType.MODE_CHANGE, by)
        return Pair(determinedTargetKey, newUiMsg)
    }

    private fun handleNumericReply(parsedMessage: ParsedIrcMessage): Pair<String?, UiChatMessage?> {
        val command = parsedMessage.command // e.g., "001", "372"
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        val targetKey = SERVER_TARGET_ID // Most numerics go to server
        var content = trailing ?: params.joinToString(" ") // Params might be part of content for some numerics
        if (command == "001") { // Welcome message, also sets confirmed nickname
            content = trailing ?: params.drop(1).joinToString(" ") // Nick is param 0
            val confirmedNick = params.firstOrNull()
            if (confirmedNick != null && !confirmedNick.equals(this.currentNickname, ignoreCase = true)) {
                this.currentNickname = confirmedNick
            }
        }
        content = replaceEmoticonsWithEmoji(content)
        val uiMsg = UiChatMessage("[INFO] $content", UiMessageType.SERVER_INFO, parsedMessage.prefix ?: "Server")
        return Pair(targetKey, uiMsg)
    }

    private fun handleErrorReply(parsedMessage: ParsedIrcMessage): Pair<String?, UiChatMessage?> {
        val command = parsedMessage.command // e.g., "401", "433"
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing

        val targetKey = _activeTarget.value ?: SERVER_TARGET_ID // Errors usually relevant to active context
        val errorParams = params.joinToString(" ")
        var errorTrailing = trailing ?: ""; errorTrailing = replaceEmoticonsWithEmoji(errorTrailing)
        val errorMessage = "Error $command: $errorParams $errorTrailing"
        val uiMsg = UiChatMessage(errorMessage, UiMessageType.SERVER_INFO, parsedMessage.prefix ?: "Server")
        return Pair(targetKey, uiMsg)
    }

    private fun handleOtherCommand(parsedMessage: ParsedIrcMessage): Pair<String?, UiChatMessage?> {
        val command = parsedMessage.command
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing
        val prefix = parsedMessage.prefix

        val targetKey = _activeTarget.value ?: SERVER_TARGET_ID
        var fullOriginalText = "${prefix?.let { ":$it " } ?: ""}$command ${params.joinToString(" ")}${trailing?.let { " :$it" } ?: ""}"
        fullOriginalText = replaceEmoticonsWithEmoji(fullOriginalText)
        val uiMsg = UiChatMessage("[${command.uppercase()}] $fullOriginalText", UiMessageType.OTHER_COMMAND, prefix)
        return Pair(targetKey, uiMsg)
    }
    // ------------ END OF REFACTORED IRC COMMAND HANDLERS ------------


    private fun processIncomingParsedMessage(parsedMessage: ParsedIrcMessage) {
        val command = parsedMessage.command
        var targetKey: String? = null // To be determined by handler
        var uiMsg: UiChatMessage? = null // To be determined by handler

        // Note: handleNick and handleQuit manage their own messages internally due to their broad impact.
        when (command) {
            "PRIVMSG" -> handlePrivmsg(parsedMessage).let { targetKey = it.first; uiMsg = it.second }
            "NOTICE" -> handleNotice(parsedMessage).let { targetKey = it.first; uiMsg = it.second }
            "JOIN" -> handleJoin(parsedMessage).let { targetKey = it.first; uiMsg = it.second }
            "PART" -> handlePart(parsedMessage).let { targetKey = it.first; uiMsg = it.second }
            "QUIT" -> handleQuit(parsedMessage) // Manages its own messages
            "NICK" -> handleNick(parsedMessage) // Manages its own messages and state updates
            "MODE" -> handleMode(parsedMessage).let { targetKey = it.first; uiMsg = it.second }
            // Numeric replies (001-399 typically)
            in "001".."399" -> handleNumericReply(parsedMessage).let { targetKey = it.first; uiMsg = it.second }
            // Error replies (400-599 typically)
            in "400".."599" -> handleErrorReply(parsedMessage).let { targetKey = it.first; uiMsg = it.second }
            else -> handleOtherCommand(parsedMessage).let { targetKey = it.first; uiMsg = it.second }
        }

        if (targetKey != null && uiMsg != null) {
            addMessageToTarget(targetKey, uiMsg!!)
            Log.d("MainViewModel", "Msg for [$targetKey]: $uiMsg")
        } else if (command !in listOf("NICK", "QUIT")) {
            // Log if a message was expected but not generated, excluding commands that handle messages internally.
            Log.d("MainViewModel", "No general UI message generated for command: $command. TargetKey: $targetKey, UiMsg: $uiMsg")
        }
    }

    fun connect(nickname: String, ssl: Boolean) {
        this.currentNickname = nickname
        val hostToConnect = defaultHost; val portToConnect = if (ssl) 6697 else 6667
        Log.d("MainViewModel", "connect: Nick: $nickname, Host: $hostToConnect, Port: $portToConnect, SSL: $ssl")
        _chatTargets.value = ensureServerTargetIsFirst(listOf(SERVER_TARGET_ID))
        _activeTarget.value = SERVER_TARGET_ID
        _allMessages.value = mapOf(SERVER_TARGET_ID to emptyList())
        _unreadTargets.value = emptySet()
        addMessageToTarget(SERVER_TARGET_ID, UiChatMessage("Conectando a $hostToConnect como $nickname...", UiMessageType.SYSTEM_MESSAGE))
        ircRepository.connect(hostToConnect, portToConnect, ssl, nickname)
    }

    fun joinChannel(channelName: String) {
        if (!channelName.startsWith("#")) { addMessageToTarget(_activeTarget.value ?: SERVER_TARGET_ID, UiChatMessage("Nombre de canal inválido: $channelName. Debe empezar con #.", UiMessageType.SYSTEM_MESSAGE)); return }
        if (connectionState.value && channelName.isNotBlank()) {
            addMessageToTarget(_activeTarget.value ?: SERVER_TARGET_ID, UiChatMessage("Solicitando unirse a $channelName...", UiMessageType.SYSTEM_MESSAGE))
            ircRepository.joinChannel(channelName)
        } else { Log.w("MainViewModel", "joinChannel: No conectado o channelName vacío."); addMessageToTarget(_activeTarget.value ?: SERVER_TARGET_ID, UiChatMessage("No se puede unir al canal. No conectado.", UiMessageType.SYSTEM_MESSAGE)) }
    }

    fun openPrivateMessage(nick: String) {
        if (nick.isBlank() || nick.startsWith("#") || nick.equals(currentNickname, ignoreCase = true)) { addMessageToTarget(_activeTarget.value ?: SERVER_TARGET_ID, UiChatMessage("Nombre de usuario inválido para chat privado: $nick", UiMessageType.SYSTEM_MESSAGE)); return }
        if (!_chatTargets.value.any{it.equals(nick, ignoreCase=true)}) {
            _chatTargets.value = ensureServerTargetIsFirst((_chatTargets.value + nick).distinct())
        }
        _activeTarget.value = nick
        if (_allMessages.value[nick] == null) { _allMessages.value = _allMessages.value + (nick to emptyList()) }
        if (_unreadTargets.value.contains(nick)) {
            _unreadTargets.value = _unreadTargets.value - nick
        }
        addMessageToTarget(nick, UiChatMessage("Chat privado con $nick iniciado.", UiMessageType.SYSTEM_MESSAGE))
    }

    fun setActiveTarget(targetName: String) {
        if (_chatTargets.value.any{it.equals(targetName, ignoreCase = true)} || targetName == SERVER_TARGET_ID) {
            _activeTarget.value = targetName
            if (_unreadTargets.value.contains(targetName)) {
                _unreadTargets.value = _unreadTargets.value - targetName
                Log.d("MainViewModel", "Target '$targetName' marked as read.")
            }
        }
        else { Log.w("MainViewModel", "Intento de activar target no existente: $targetName. Targets: ${_chatTargets.value}"); if (_chatTargets.value.isNotEmpty()) { _activeTarget.value = _chatTargets.value.first() } else { _activeTarget.value = SERVER_TARGET_ID } }
    }

    fun closeTarget(targetName: String) {
        if (targetName == SERVER_TARGET_ID) { addMessageToTarget(SERVER_TARGET_ID, UiChatMessage("La pestaña '$SERVER_TARGET_ID' no se puede cerrar.", UiMessageType.SYSTEM_MESSAGE)); return }

        val wasUnread = _unreadTargets.value.contains(targetName)

        if (targetName.startsWith("#")) {
            Log.d("MainViewModel", "Solicitando PART para el canal: $targetName vía repositorio");
            ircRepository.partChannel(targetName)
        } else {
            _chatTargets.value = ensureServerTargetIsFirst(_chatTargets.value.filterNot { it.equals(targetName, ignoreCase = true) })
            if (wasUnread) {
                _unreadTargets.value = _unreadTargets.value - targetName
                Log.d("MainViewModel", "PM Target '$targetName' removed from unread due to close.")
            }
            if (_activeTarget.value?.equals(targetName, ignoreCase = true) == true) {
                _activeTarget.value = _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
                _activeTarget.value?.let {
                    if (_unreadTargets.value.contains(it)) {
                        _unreadTargets.value = _unreadTargets.value - it
                        Log.d("MainViewModel", "New active target '$it' after close, marked as read.")
                    }
                }
            }
        }
    }

    fun sendMessage(messageContent: String) {
        viewModelScope.launch {
            val currentActiveTarget = _activeTarget.value
            if (connectionState.value && !currentActiveTarget.isNullOrBlank() && messageContent.isNotBlank()) {
                if (currentActiveTarget == SERVER_TARGET_ID) { addMessageToTarget(SERVER_TARGET_ID, UiChatMessage("No puedes enviar mensajes a la pestaña '$SERVER_TARGET_ID'. Abre un canal o PM.", UiMessageType.SYSTEM_MESSAGE)); return@launch }
                val isToChannel = currentActiveTarget.startsWith("#"); val type = if (isToChannel) UiMessageType.CHANNEL_MSG_SENT else UiMessageType.PRIVATE_MSG_SENT
                val processedMessageContentForUi = replaceEmoticonsWithEmoji(messageContent)
                val fullText = "<${currentNickname}> $processedMessageContentForUi"
                val uiMessage = UiChatMessage(fullText = fullText, type = type, sender = currentNickname, isOwnMessage = true)
                addMessageToTarget(currentActiveTarget, uiMessage)
                ircRepository.sendMessage(currentActiveTarget, messageContent)
            } else { Log.w("MainViewModel", "No se puede enviar mensaje. Estado: ${connectionState.value}, Target: $currentActiveTarget, Msg: $messageContent"); val targetForError = _activeTarget.value ?: _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID; addMessageToTarget(targetForError, UiChatMessage("No se puede enviar el mensaje. Verifica la conexión y el target.", UiMessageType.SYSTEM_MESSAGE)) }
        }
    }

    @Deprecated("Usar disconnectFromServerAndStopService para una desconexión completa.", ReplaceWith("disconnectFromServerAndStopService()"))
    fun disconnect() {
        val targetForMessage = _activeTarget.value ?: _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
        addMessageToTarget(targetForMessage, UiChatMessage("Solicitando desconexión del socket IRC...", UiMessageType.SYSTEM_MESSAGE))
        ircRepository.disconnect()
    }

    fun disconnectFromServerAndStopService() {
        Log.i("MainViewModel", "disconnectFromServerAndStopService llamado.")
        val targetForMessage = _activeTarget.value ?: _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
        addMessageToTarget(targetForMessage, UiChatMessage("Desconectando y solicitando detener el servicio...", UiMessageType.SYSTEM_MESSAGE))

        viewModelScope.launch {
            _userMessageEvents.emit("Desconectado del servidor.")
        }

        ircRepository.disconnectAndStopService()
    }
}
