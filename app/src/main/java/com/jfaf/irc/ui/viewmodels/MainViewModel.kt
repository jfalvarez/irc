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

    // MODIFIED: Renamed and changed type to carry the targetKey (sender of PM)
    private val _incomingPrivateMessageEvent = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val incomingPrivateMessageEvent: SharedFlow<String> = _incomingPrivateMessageEvent.asSharedFlow()

    private val _userMessageEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val userMessageEvents: SharedFlow<String> = _userMessageEvents.asSharedFlow()
    
    private val _unreadTargets = MutableStateFlow<Set<String>>(emptySet())
    val unreadTargets: StateFlow<Set<String>> = _unreadTargets.asStateFlow()

    private val emoticonToEmojiMap = mapOf(
        ":)" to "😊", ":-)" to "😊", ":D" to "😀", ":-D" to "😀",
        ";)" to "😉", ";-)" to "😉", ":(" to "😞", ":-(" to "😞",
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
        val mutableTargets = targets.toMutableList()
        val serverPresent = mutableTargets.remove("Servidor")
        val distinctTargets = mutableTargets.distinct().toMutableList() 
        if (serverPresent) {
            distinctTargets.add(0, "Servidor")
        }
        if (distinctTargets.isEmpty() && serverPresent) {
             distinctTargets.add("Servidor") 
        } else if (distinctTargets.isEmpty() && targets.contains("Servidor")) {
             distinctTargets.add("Servidor") 
        }
        return distinctTargets.toList()
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
                val serverMessages = _allMessages.value["Servidor"] ?: emptyList()
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

                _chatTargets.value = ensureServerTargetIsFirst(listOf("Servidor")) 
                _activeTarget.value = "Servidor"
                _allMessages.value = mapOf("Servidor" to updatedServerMessages)
                 _unreadTargets.value = emptySet() 
                Log.i("MainViewModel", "UI actualizada para reflejar desconexión del servicio. Mensaje añadido: $messageText")
            } else {
                val serverMessages = _allMessages.value["Servidor"] ?: emptyList()
                val lastMessageText = serverMessages.lastOrNull()?.fullText ?: ""

                if (_chatTargets.value.isEmpty() || _activeTarget.value == null || !lastMessageText.contains("Conectado al servidor", ignoreCase = true)) {
                    _chatTargets.value = ensureServerTargetIsFirst((_chatTargets.value + "Servidor").distinct())
                    _activeTarget.value = _activeTarget.value ?: "Servidor"
                     val updatedMessages = (serverMessages + UiChatMessage("Conectado al servidor.", UiMessageType.SYSTEM_MESSAGE)).takeLast(maxUiMessagesPerTarget)
                    _allMessages.value = _allMessages.value + ("Servidor" to updatedMessages)
                }
                Log.i("MainViewModel", "Servicio conectado.")
            }
        }.launchIn(viewModelScope)
    }

    private fun addMessageToTarget(target: String, message: UiChatMessage) {
        val currentMessagesForTarget = _allMessages.value[target] ?: emptyList()
        val updatedMessagesForTarget = (currentMessagesForTarget + message).takeLast(maxUiMessagesPerTarget)
        _allMessages.value = _allMessages.value + (target to updatedMessagesForTarget)
    }

    private fun processIncomingParsedMessage(parsedMessage: ParsedIrcMessage) {
        val sender = parsedMessage.senderNickname
        val command = parsedMessage.command
        val params = parsedMessage.params
        val trailing = parsedMessage.trailing
        var targetKey: String? = null 
        val uiMsg: UiChatMessage?

        when (command) {
            "PRIVMSG" -> {
                val msgTarget = params.firstOrNull() ?: return
                var content = trailing ?: ""
                content = replaceEmoticonsWithEmoji(content) 
                val isToChannel = msgTarget.startsWith("#")
                val currentIsOwn = sender?.equals(currentNickname, ignoreCase = true) == true 
                targetKey = if (isToChannel) msgTarget else if (currentIsOwn) msgTarget else sender 
                
                if (targetKey != null) { 
                    if (!currentIsOwn) { // Message is from someone else
                        if (!targetKey.equals(_activeTarget.value, ignoreCase = true)) {
                            _unreadTargets.value = _unreadTargets.value + targetKey
                            Log.d("MainViewModel", "Target '$targetKey' marked as unread.")
                        }
                        // MODIFIED: Emit event for ALL incoming PMs not from self
                        if (!isToChannel) {
                            _incomingPrivateMessageEvent.tryEmit(targetKey)
                            Log.d("MainViewModel", "Incoming PM from '$sender' for target '$targetKey'. Event emitted.")
                        }
                    }

                    if (targetKey != "Servidor" && !isToChannel && !currentIsOwn ) {
                         if (!_chatTargets.value.any{ it.equals(targetKey, ignoreCase = true)}) {
                            Log.d("MainViewModel", "PRIVMSG: Adding new PM target '$targetKey' to _chatTargets.")
                            _chatTargets.value = ensureServerTargetIsFirst((_chatTargets.value + targetKey).distinct())
                            if(_allMessages.value[targetKey] == null) { 
                                _allMessages.value = _allMessages.value + (targetKey to emptyList())
                            }
                        }
                    }
                }
                val messageText = when { isToChannel -> "<${sender}> $content"; currentIsOwn -> "<${currentNickname}> $content"; else -> "<${sender}> $content" }
                uiMsg = UiChatMessage(fullText = messageText, type = when { currentIsOwn && isToChannel -> UiMessageType.CHANNEL_MSG_SENT; currentIsOwn && !isToChannel -> UiMessageType.PRIVATE_MSG_SENT; !currentIsOwn && isToChannel -> UiMessageType.CHANNEL_MSG_RECEIVED; else -> UiMessageType.PRIVATE_MSG_RECEIVED }, sender = sender, isOwnMessage = currentIsOwn)
            }
            "NOTICE" -> {
                val noticeTargetParam = params.firstOrNull()
                val from = sender ?: parsedMessage.prefix ?: "Server"
                var content = trailing ?: params.joinToString(" "); content = replaceEmoticonsWithEmoji(content) 
                targetKey = if (noticeTargetParam?.equals(currentNickname, ignoreCase = true) == true && sender != null) sender else _activeTarget.value ?: "Servidor"
                uiMsg = UiChatMessage("-$from- $content", UiMessageType.NOTICE, from)
                if (targetKey != null && sender != null && noticeTargetParam?.equals(currentNickname, ignoreCase = true) == true && !targetKey.equals(_activeTarget.value, ignoreCase = true)) {
                    _unreadTargets.value = _unreadTargets.value + targetKey
                    Log.d("MainViewModel", "NOTICE Target '$targetKey' marked as unread.")
                }
            }
            "JOIN" -> {
                targetKey = trailing ?: params.firstOrNull() ?: return
                if (sender?.equals(currentNickname, ignoreCase = true) == true) {
                    if (!_chatTargets.value.any{ it.equals(targetKey, ignoreCase = true)}) {
                        Log.d("MainViewModel", "JOIN: Adding target '$targetKey' to _chatTargets.")
                        _chatTargets.value = ensureServerTargetIsFirst((_chatTargets.value + targetKey).distinct())
                    }
                    _activeTarget.value = targetKey 
                    if (_unreadTargets.value.contains(targetKey)) {
                        _unreadTargets.value = _unreadTargets.value - targetKey
                    }
                }
                uiMsg = UiChatMessage("* ${sender ?: "Alguien"} ha entrado a $targetKey", UiMessageType.JOIN_PART_QUIT, sender)
            }
            "PART" -> {
                var channelName = params.firstOrNull(); if (channelName.isNullOrBlank() && !trailing.isNullOrBlank() && trailing.startsWith("#")) { channelName = trailing.split(" ")[0] }; if (channelName.isNullOrBlank()) { Log.w("MainViewModel", "PART sin nombre de canal. Params: $params, Trailing: $trailing"); return }; targetKey = channelName
                val reasonPart = if (trailing != channelName) trailing?.substringAfter(channelName)?.trim() else null; var reasonMsgContent = reasonPart?.let { if(it.startsWith(":")) it.substring(1) else it } ?: ""; reasonMsgContent = replaceEmoticonsWithEmoji(reasonMsgContent); val reasonMsg = if (reasonMsgContent.isNotBlank()) " ($reasonMsgContent)" else ""
                uiMsg = UiChatMessage("* ${sender ?: "Alguien"} ha salido de $targetKey$reasonMsg", UiMessageType.JOIN_PART_QUIT, sender)
                if (sender?.equals(currentNickname, ignoreCase = true) == true) {
                    if (_chatTargets.value.any { it.equals(targetKey, ignoreCase = true) }) { 
                        _chatTargets.value = ensureServerTargetIsFirst(_chatTargets.value.filterNot { it.equals(targetKey, ignoreCase = true) })
                        if (_unreadTargets.value.contains(targetKey)) {
                            _unreadTargets.value = _unreadTargets.value - targetKey
                        }
                        if (_activeTarget.value?.equals(targetKey, ignoreCase = true) == true) { _activeTarget.value = _chatTargets.value.firstOrNull() ?: "Servidor" }
                    }
                }
            }
            "QUIT" -> { var reason = trailing?.let { " ($it)" } ?: ""; reason = replaceEmoticonsWithEmoji(reason); val quitMessage = "* ${sender ?: "Alguien"} ha salido del IRC$reason"; ArrayList(_chatTargets.value).forEach { openTarget -> if (openTarget.startsWith("#")) { addMessageToTarget(openTarget, UiChatMessage(quitMessage, UiMessageType.JOIN_PART_QUIT, sender)) } }; return }
            "NICK" -> {
                val oldNick = sender ?: return; val newNick = trailing ?: params.firstOrNull() ?: return; val nickChangeMsg = UiChatMessage("* $oldNick ahora es conocido como $newNick", UiMessageType.NICK_CHANGE, oldNick)
                val updatedAllMessages = _allMessages.value.toMutableMap(); var activeTargetPotentiallyChanged = false
                _allMessages.value.keys.forEach { target -> if (target.equals(oldNick, ignoreCase = true)) { updatedAllMessages.remove(target)?.let { messages -> updatedAllMessages[newNick] = (messages + nickChangeMsg).takeLast(maxUiMessagesPerTarget) }; if (_activeTarget.value?.equals(oldNick, ignoreCase = true) == true) activeTargetPotentiallyChanged = true } else { if (_chatTargets.value.any{ it.equals(target,ignoreCase=true)} && (_allMessages.value[target]?.any { it.sender?.equals(oldNick,ignoreCase=true) == true || it.fullText.contains(oldNick, ignoreCase = true) } == true)) { updatedAllMessages[target] = (updatedAllMessages[target]!! + nickChangeMsg).takeLast(maxUiMessagesPerTarget) } } }; _allMessages.value = updatedAllMessages
                if (_chatTargets.value.any{it.equals(oldNick, ignoreCase = true)}) { _chatTargets.value = ensureServerTargetIsFirst(_chatTargets.value.map { if (it.equals(oldNick, ignoreCase = true)) newNick else it }.distinct()); if (activeTargetPotentiallyChanged) { _activeTarget.value = newNick } }
                if (_unreadTargets.value.contains(oldNick)) {
                    _unreadTargets.value = (_unreadTargets.value - oldNick) + newNick
                }
                if (oldNick.equals(this.currentNickname, ignoreCase = true)) { this.currentNickname = newNick; Log.d("MainViewModel", "currentNickname actualizado a: $newNick por NICK.") }; return 
            }
            "MODE" -> { targetKey = params.firstOrNull(); if (targetKey.isNullOrBlank() || (!targetKey.startsWith("#") && !targetKey.equals(currentNickname, ignoreCase = true)) ) { targetKey = _activeTarget.value ?: "Servidor" }; val by = sender ?: parsedMessage.prefix ?: "Server"; var modes = params.drop(1).joinToString(" ") + (trailing?.let { " :$it" } ?: ""); modes = replaceEmoticonsWithEmoji(modes); uiMsg = UiChatMessage("* $by establece modo $modes en $targetKey", UiMessageType.MODE_CHANGE, by) }
            "001" -> { targetKey = "Servidor"; var welcomeMessage = trailing ?: params.drop(1).joinToString(" "); welcomeMessage = replaceEmoticonsWithEmoji(welcomeMessage); val confirmedNick = params.firstOrNull(); if (confirmedNick != null && !confirmedNick.equals(this.currentNickname, ignoreCase = true)) { this.currentNickname = confirmedNick }; uiMsg = UiChatMessage("[INFO] $welcomeMessage", UiMessageType.SERVER_INFO, parsedMessage.prefix ?: "Server") }
            "002", "003", "004", "005", "250", "251", "252", "253", "254", "255", "265", "266", "372", "375", "376" -> { targetKey = "Servidor"; var content = trailing ?: params.joinToString(" "); content = replaceEmoticonsWithEmoji(content); uiMsg = UiChatMessage("[INFO] $content", UiMessageType.SERVER_INFO, parsedMessage.prefix ?: "Server") }
            "401", "403", "404", "405", "406", "407", "431", "432", "433", "436", "437", "471", "473", "474", "475", "477"  -> { targetKey = _activeTarget.value ?: "Servidor"; val commandString = parsedMessage.command; val errorParams = parsedMessage.params.joinToString(" "); var errorTrailing = parsedMessage.trailing ?: ""; errorTrailing = replaceEmoticonsWithEmoji(errorTrailing); val errorMessage = "Error $commandString: $errorParams $errorTrailing"; uiMsg = UiChatMessage(errorMessage, UiMessageType.SERVER_INFO, parsedMessage.prefix ?: "Server") }
            else -> { targetKey = _activeTarget.value ?: "Servidor"; var fullOriginalText = "${parsedMessage.prefix?.let { ":$it " } ?: ""}$command ${params.joinToString(" ")}${trailing?.let { " :$it" } ?: ""}"; fullOriginalText = replaceEmoticonsWithEmoji(fullOriginalText); uiMsg = UiChatMessage("[${command.uppercase()}] $fullOriginalText", UiMessageType.OTHER_COMMAND, parsedMessage.prefix) }
        }
        if (targetKey != null && uiMsg != null) { addMessageToTarget(targetKey, uiMsg); Log.d("MainViewModel", "Msg for [$targetKey]: $uiMsg") }
    }

    fun connect(nickname: String, ssl: Boolean) { 
        this.currentNickname = nickname 
        val hostToConnect = defaultHost; val portToConnect = if (ssl) 6697 else 6667 
        Log.d("MainViewModel", "connect: Nick: $nickname, Host: $hostToConnect, Port: $portToConnect, SSL: $ssl")
        _chatTargets.value = ensureServerTargetIsFirst(listOf("Servidor"))
        _activeTarget.value = "Servidor"
        _allMessages.value = mapOf("Servidor" to emptyList())
        _unreadTargets.value = emptySet() 
        addMessageToTarget("Servidor", UiChatMessage("Conectando a $hostToConnect como $nickname...", UiMessageType.SYSTEM_MESSAGE))
        ircRepository.connect(hostToConnect, portToConnect, ssl, nickname)
    }

    fun joinChannel(channelName: String) {
        if (!channelName.startsWith("#")) { addMessageToTarget(_activeTarget.value ?: "Servidor", UiChatMessage("Nombre de canal inválido: $channelName. Debe empezar con #.", UiMessageType.SYSTEM_MESSAGE)); return }
        if (connectionState.value && channelName.isNotBlank()) {
            addMessageToTarget(_activeTarget.value ?: "Servidor", UiChatMessage("Solicitando unirse a $channelName...", UiMessageType.SYSTEM_MESSAGE))
            ircRepository.joinChannel(channelName)
        } else { Log.w("MainViewModel", "joinChannel: No conectado o channelName vacío."); addMessageToTarget(_activeTarget.value ?: "Servidor", UiChatMessage("No se puede unir al canal. No conectado.", UiMessageType.SYSTEM_MESSAGE)) }
    }
    
    fun openPrivateMessage(nick: String) {
        if (nick.isBlank() || nick.startsWith("#") || nick.equals(currentNickname, ignoreCase = true)) { addMessageToTarget(_activeTarget.value ?: "Servidor", UiChatMessage("Nombre de usuario inválido para chat privado: $nick", UiMessageType.SYSTEM_MESSAGE)); return }
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
        if (_chatTargets.value.any{it.equals(targetName, ignoreCase = true)} || targetName == "Servidor") { 
            _activeTarget.value = targetName 
            if (_unreadTargets.value.contains(targetName)) {
                _unreadTargets.value = _unreadTargets.value - targetName
                 Log.d("MainViewModel", "Target '$targetName' marked as read.")
            }
        } 
        else { Log.w("MainViewModel", "Intento de activar target no existente: $targetName. Targets: ${_chatTargets.value}"); if (_chatTargets.value.isNotEmpty()) { _activeTarget.value = _chatTargets.value.first() } else { _activeTarget.value = "Servidor" } }
    }

    fun closeTarget(targetName: String) { 
        if (targetName == "Servidor") { addMessageToTarget("Servidor", UiChatMessage("La pestaña 'Servidor' no se puede cerrar.", UiMessageType.SYSTEM_MESSAGE)); return }
        
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
                _activeTarget.value = _chatTargets.value.firstOrNull() ?: "Servidor" 
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
                if (currentActiveTarget == "Servidor") { addMessageToTarget("Servidor", UiChatMessage("No puedes enviar mensajes a la pestaña 'Servidor'. Abre un canal o PM.", UiMessageType.SYSTEM_MESSAGE)); return@launch }
                val isToChannel = currentActiveTarget.startsWith("#"); val type = if (isToChannel) UiMessageType.CHANNEL_MSG_SENT else UiMessageType.PRIVATE_MSG_SENT
                val processedMessageContentForUi = replaceEmoticonsWithEmoji(messageContent)
                val fullText = "<${currentNickname}> $processedMessageContentForUi" 
                val uiMessage = UiChatMessage(fullText = fullText, type = type, sender = currentNickname, isOwnMessage = true)
                addMessageToTarget(currentActiveTarget, uiMessage) 
                ircRepository.sendMessage(currentActiveTarget, messageContent)
            } else { Log.w("MainViewModel", "No se puede enviar mensaje. Estado: ${connectionState.value}, Target: $currentActiveTarget, Msg: $messageContent"); val targetForError = _activeTarget.value ?: _chatTargets.value.firstOrNull() ?: "Servidor"; addMessageToTarget(targetForError, UiChatMessage("No se puede enviar el mensaje. Verifica la conexión y el target.", UiMessageType.SYSTEM_MESSAGE)) }
        }
    }

    @Deprecated("Usar disconnectFromServerAndStopService para una desconexión completa.", ReplaceWith("disconnectFromServerAndStopService()"))
    fun disconnect() { 
        val targetForMessage = _activeTarget.value ?: _chatTargets.value.firstOrNull() ?: "Servidor"
        addMessageToTarget(targetForMessage, UiChatMessage("Solicitando desconexión del socket IRC...", UiMessageType.SYSTEM_MESSAGE))
        ircRepository.disconnect() 
    }

    fun disconnectFromServerAndStopService() {
        Log.i("MainViewModel", "disconnectFromServerAndStopService llamado.")
        val targetForMessage = _activeTarget.value ?: _chatTargets.value.firstOrNull() ?: "Servidor"
        addMessageToTarget(targetForMessage, UiChatMessage("Desconectando y solicitando detener el servicio...", UiMessageType.SYSTEM_MESSAGE))
        
        viewModelScope.launch { 
            _userMessageEvents.emit("Desconectado del servidor.")
        }
        
        ircRepository.disconnectAndStopService() 
    }
}
