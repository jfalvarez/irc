package com.jfaf.irc.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.data.prefs.UserPreferencesRepository
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
    val isOwnMessage: Boolean = false,
    val imageUrl: String? = null // Nuevo campo para la URL de la imagen
)

enum class UiMessageType {
    CHANNEL_MSG_RECEIVED,
    CHANNEL_MSG_SENT,
    PRIVATE_MSG_RECEIVED,
    PRIVATE_MSG_SENT,
    JOIN_PART_QUIT,
    NICK_CHANGE,
    MODE_CHANGE,
    NOTICE,
    SERVER_INFO,
    SYSTEM_MESSAGE,
    OTHER_COMMAND,
    UNKNOWN
}

// --- Grouped State Flows ---
data class ChatScreenState(
    val activeTarget: StateFlow<String?>,
    val chatTargets: StateFlow<List<String>>,
    val unreadTargets: StateFlow<Set<String>>,
    val uiMessages: StateFlow<List<UiChatMessage>>,
    val connectionState: StateFlow<Boolean>,
    val usersInChannel: StateFlow<Map<String, List<String>>>,
    val currentChannelUserList: StateFlow<List<String>>,
    val showUserList: StateFlow<Boolean> 
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val ircRepository: IrcRepository,
    private val ircMessageHandler: IrcMessageHandler,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel(), ChatEventListener {

    private var chatEventOrchestrator = ChatEventOrchestrator(
        ircRepository,
        userPreferencesRepository,
        this, // MainViewModel is the ChatEventListener
        viewModelScope
    )

    // --- Private data class for holding combined filter parameters ---
    private data class UiMessagesFilterContext(
        val activeTarget: String?,
        val allMessages: Map<String, List<UiChatMessage>>,
        val showJpq: Boolean,
        val showNick: Boolean,
        val showMode: Boolean,
        val ignoredUsers: Set<String>
    )

    var currentNickname = "IrcUser${(100..999).random()}"
        private set
    private val defaultHost = "irc.irc-hispano.org"
    private val SERVER_TARGET_ID = "Servidor"

    // --- Event Flows (remain separate) ---
    private val _incomingPrivateMessageEvent = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val incomingPrivateMessageEvent: SharedFlow<String> = _incomingPrivateMessageEvent.asSharedFlow()

    private val _userMessageEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val userMessageEvents: SharedFlow<String> = _userMessageEvents.asSharedFlow()

    // --- Internal Mutable State Flows ---
    private val _unreadTargets = MutableStateFlow<Set<String>>(emptySet())
    private val _chatTargets = MutableStateFlow<List<String>>(emptyList())
    private val _activeTarget = MutableStateFlow<String?>(null)
    private val _allMessages = MutableStateFlow<Map<String, List<UiChatMessage>>>(emptyMap())
    private val _usersInChannel = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    // --- MODIFICAR VALOR INICIAL AQUÍ ---
    private val _showUserList = MutableStateFlow(false) // De true a false

    // --- Preferences Flows (used internally for uiMessages combine logic) ---
    private val showJoinPartQuitMessagesPref: StateFlow<Boolean> =
        userPreferencesRepository.showJoinPartQuitFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val showNickChangesPref: StateFlow<Boolean> =
        userPreferencesRepository.showNickChangesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val showModeChangesPref: StateFlow<Boolean> =
        userPreferencesRepository.showModeChangesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val showPingPongMessagesPref: StateFlow<Boolean> = // Nueva preferencia
        userPreferencesRepository.showPingPongMessagesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false // Por defecto no mostrar mensajes PING
        )

    private val ignoredUsersPref: StateFlow<Set<String>> =
        userPreferencesRepository.ignoredUsersFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    // This produces the uiMessages StateFlow that will be part of ChatScreenState
    @Suppress("UNCHECKED_CAST")
    private val combinedUiMessagesFlow: StateFlow<List<UiChatMessage>> = combine(
        listOf(
            _activeTarget,
            _allMessages,
            showJoinPartQuitMessagesPref,
            showNickChangesPref,
            showModeChangesPref,
            ignoredUsersPref
            // No es necesario incluir showPingPongMessagesPref aquí, ya que el filtrado de PING se hace antes.
        )
    ) { values ->
        val filterContext = UiMessagesFilterContext(
            activeTarget = values[0] as String?,
            allMessages = values[1] as Map<String, List<UiChatMessage>>,
            showJpq = values[2] as Boolean,
            showNick = values[3] as Boolean,
            showMode = values[4] as Boolean,
            ignoredUsers = values[5] as Set<String>
        )
        val messagesForTarget = filterContext.allMessages[filterContext.activeTarget] ?: emptyList()
        val ignoredUsersLowercase = filterContext.ignoredUsers.map { it.lowercase() }.toSet()
        messagesForTarget.filter { message ->
            var shouldShow = true
            if (message.sender != null &&
                (message.type == UiMessageType.CHANNEL_MSG_RECEIVED || message.type == UiMessageType.PRIVATE_MSG_RECEIVED) &&
                message.sender.lowercase() in ignoredUsersLowercase) {
                shouldShow = false
            }
            if (shouldShow && !filterContext.showJpq) {
                if (message.type == UiMessageType.JOIN_PART_QUIT ||
                    message.fullText.contains("signed off", ignoreCase = true) ||
                    message.fullText.contains("connection closed", ignoreCase = true)) {
                    shouldShow = false
                }
            }
            if (shouldShow && !filterContext.showNick) {
                if (message.type == UiMessageType.NICK_CHANGE) {
                    shouldShow = false
                }
            }
            if (shouldShow && !filterContext.showMode) {
                if (message.type == UiMessageType.MODE_CHANGE) {
                    shouldShow = false
                }
            }
            shouldShow
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    // --- StateFlow para la lista de usuarios del canal activo ---
    private val currentChannelUserListFlow: StateFlow<List<String>> = combine(
        _activeTarget,
        _usersInChannel
    ) { activeTarget, usersMap ->
        usersMap[activeTarget] ?: emptyList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    // --- Public Grouped State --- 
    val chatScreenState: ChatScreenState = ChatScreenState(
        activeTarget = _activeTarget.asStateFlow(),
        chatTargets = _chatTargets.asStateFlow(),
        unreadTargets = _unreadTargets.asStateFlow(),
        uiMessages = combinedUiMessagesFlow,
        connectionState = ircRepository.connectionState,
        usersInChannel = _usersInChannel.asStateFlow(),
        currentChannelUserList = currentChannelUserListFlow,
        showUserList = _showUserList.asStateFlow()
    )

    init {
        chatEventOrchestrator.startObservingRawMessages()

        // Observe connection state directly from repository for internal ViewModel logic
        ircRepository.connectionState.onEach { isConnected ->
            Log.i("MainViewModel", "Estado de conexión (desde Servicio): ${if (isConnected) "CONECTADO" else "DESCONECTADO"}")
            if (!isConnected) {
                handleServiceDisconnected()
            } else {
                handleServiceConnected()
            }
        }.launchIn(viewModelScope)
    }

    // --- ChatEventListener Implementation ---
    override fun processMessageForUi(parsedMessage: ParsedIrcMessage, ignoredUsersLowercase: Set<String>) {
        // Filtrar mensajes PING si la preferencia está desactivada
        if (parsedMessage.command.equals("PING", ignoreCase = true) && !showPingPongMessagesPref.value) {
            Log.d("MainViewModel.processMessageForUi", "PING message received and ignored for UI based on preference.")
            return
        }

        val snapshot = ChatUiSnapshot(
            currentNickname = this.currentNickname,
            activeTarget = _activeTarget.value, 
            allMessages = _allMessages.value, 
            chatTargets = _chatTargets.value, 
            unreadTargets = _unreadTargets.value, 
            usersInChannel = _usersInChannel.value 
        )
        val result = ircMessageHandler.processMessage(snapshot, parsedMessage)
        
        this.currentNickname = result.newCurrentNickname
        _activeTarget.value = result.newActiveTarget
        _allMessages.value = result.newAllMessages
        _chatTargets.value = result.newChatTargets
        _unreadTargets.value = result.newUnreadTargets
        _usersInChannel.value = result.newUsersInChannel
        
        Log.d("MainViewModel.processMessageForUi", "Post-update: activeTarget='${_activeTarget.value}', allMessages keys='${_allMessages.value.keys.joinToString()}', chatTargets='${_chatTargets.value.joinToString()}', unread='${_unreadTargets.value.joinToString()}', usersInChannel keys='${_usersInChannel.value.keys.joinToString()}'") 
        
        result.ownNickChangedTo?.let {
            Log.d("MainViewModel", "Own nick change to '${it}' confirmed by IrcMessageHandler.")
        }
        result.privateMessageEventNick?.let { nick ->
            if (nick.lowercase() !in ignoredUsersLowercase) { 
                emitPrivateMessageEvent(nick) 
            } else {
                Log.d("MainViewModel", "PM Event for '$nick' from IrcMessageHandler suppressed as user is in ignored list: ${ignoredUsersLowercase.joinToString()}")
            }
        }
        if (result.uiMessageToAdd != null && result.targetForUiMessage != null) {
            Log.d("MainViewModel", "IrcMessageHandler result for ${parsedMessage.command} included a direct UiMessage for [${result.targetForUiMessage}]: ${result.uiMessageToAdd}. ViewModel state updated via newAllMessages.")
        } else {
            Log.d("MainViewModel", "IrcMessageHandler processed ${parsedMessage.command}. ViewModel state updated from result.")
        }
    }

    override fun emitPrivateMessageEvent(nick: String) {
        val currentIgnoredUsers = ignoredUsersPref.value.map { it.lowercase() }.toSet()
        if (nick.lowercase() !in currentIgnoredUsers) {
            _incomingPrivateMessageEvent.tryEmit(nick)
            Log.d("MainViewModel", "PM Event for '$nick' emitted via ChatEventListener interface.")
        } else {
            Log.d("MainViewModel", "PM Event for '$nick' (from orchestrator) suppressed as user is in current ignored list.")
        }
    }

    override fun isConnected(): Boolean = ircRepository.connectionState.value
    // --- End ChatEventListener Implementation ---

    fun toggleUserListVisibility() {
        _showUserList.value = !_showUserList.value
        Log.d("MainViewModel", "User list visibility toggled to: ${_showUserList.value}")
    }

    private fun addSystemMessageToTarget(target: String, text: String) {
        val systemMessage = UiChatMessage(text, UiMessageType.SYSTEM_MESSAGE)
        val currentMessages = _allMessages.value[target] ?: emptyList()
        _allMessages.value = _allMessages.value + (target to (currentMessages + systemMessage).takeLast(150))
    }

    private fun addLocalUiMessageToTarget(target: String, uiMessage: UiChatMessage) {
        val currentMessagesForTarget = _allMessages.value[target] ?: emptyList()
        _allMessages.value = _allMessages.value + (target to (currentMessagesForTarget + uiMessage).takeLast(150))
        Log.d("MainViewModel.LocalEcho", "Locally added to '$target': '${uiMessage.fullText}'")
    }

    private fun ensureServerTargetIsFirstLocal(targets: List<String>): List<String> {
        val otherTargets = targets.asSequence().filterNot { it == SERVER_TARGET_ID }.distinct().toList()
        return listOf(SERVER_TARGET_ID) + otherTargets
    }

    private fun handleServiceConnected() {
        chatEventOrchestrator.resetSessionState()
        val serverMessages = _allMessages.value[SERVER_TARGET_ID] ?: emptyList()
        val lastMessageText = serverMessages.lastOrNull()?.fullText ?: ""
        if (_chatTargets.value.isEmpty() || _activeTarget.value == null || !lastMessageText.contains("Conectado al servidor", ignoreCase = true)) {
            _chatTargets.value = ensureServerTargetIsFirstLocal((_chatTargets.value + SERVER_TARGET_ID).distinct())
            _activeTarget.value = _activeTarget.value ?: SERVER_TARGET_ID
            addSystemMessageToTarget(SERVER_TARGET_ID, "Conectado al servidor.")
        }
        Log.i("MainViewModel", "Servicio conectado. Auto-reply list for ignored users has been reset by orchestrator.")
        Log.d("MainViewModel.ServiceConnected", "State: activeTarget='${_activeTarget.value}', allMessages keys='${_allMessages.value.keys.joinToString()}', chatTargets='${_chatTargets.value.joinToString()}'")
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
        if (messageText != null) {
            addSystemMessageToTarget(SERVER_TARGET_ID, messageText)
        }
        _chatTargets.value = ensureServerTargetIsFirstLocal(listOf(SERVER_TARGET_ID))
        _activeTarget.value = SERVER_TARGET_ID
        val newAllMessages = mutableMapOf<String, List<UiChatMessage>>()
        _allMessages.value[SERVER_TARGET_ID]?.let { newAllMessages[SERVER_TARGET_ID] = it }
        _allMessages.value = newAllMessages.toMap()
        _unreadTargets.value = emptySet()
        _usersInChannel.value = emptyMap() 
        Log.i("MainViewModel", "UI actualizada para reflejar desconexión del servicio. usersInChannel reseteado.")
    }

    fun connect(nickname: String, ssl: Boolean) {
        this.currentNickname = nickname
        val hostToConnect = defaultHost; val portToConnect = if (ssl) 6697 else 6667
        Log.d("MainViewModel", "connect: Nick: $nickname, Host: $hostToConnect, Port: $portToConnect, SSL: $ssl")
        _chatTargets.value = ensureServerTargetIsFirstLocal(listOf(SERVER_TARGET_ID))
        _activeTarget.value = SERVER_TARGET_ID
        _allMessages.value = mapOf(SERVER_TARGET_ID to emptyList())
        _unreadTargets.value = emptySet()
        _usersInChannel.value = emptyMap() 
        Log.d("MainViewModel.Connect", "Post-init: activeTarget='${_activeTarget.value}', allMessages keys='${_allMessages.value.keys.joinToString()}', chatTargets='${_chatTargets.value.joinToString()}', usersInChannel keys='${_usersInChannel.value.keys.joinToString()}'") 
        addSystemMessageToTarget(SERVER_TARGET_ID, "Conectando a $hostToConnect como $nickname...")
        ircRepository.connect(hostToConnect, portToConnect, ssl, nickname)
    }

    fun joinChannel(channelName: String) {
        val currentActiveTarget = _activeTarget.value ?: SERVER_TARGET_ID
        if (!channelName.startsWith("#")) {
            addSystemMessageToTarget(currentActiveTarget, "Nombre de canal inválido: $channelName. Debe empezar con #.")
            return
        }
        if (ircRepository.connectionState.value && channelName.isNotBlank()) { 
            addSystemMessageToTarget(currentActiveTarget, "Solicitando unirse a $channelName...")
            ircRepository.joinChannel(channelName)
        } else {
            Log.w("MainViewModel", "joinChannel: No conectado o channelName vacío.")
            addSystemMessageToTarget(currentActiveTarget, "No se puede unir al canal. No conectado.")
        }
    }

    fun openPrivateMessage(nick: String) {
        Log.d("MainViewModel.SetActiveTarget", "Attempting to open PM with: '$nick'. Current: active='${_activeTarget.value}', chatTargets='${_chatTargets.value.joinToString()}'")
        val currentActiveTarget = _activeTarget.value ?: SERVER_TARGET_ID
        if (nick.isBlank() || nick.startsWith("#") || nick.equals(currentNickname, ignoreCase = true)) {
            addSystemMessageToTarget(currentActiveTarget, "Nombre de usuario inválido para chat privado: $nick")
            return
        }
        if (!_chatTargets.value.any{it.equals(nick, ignoreCase=true)}) {
            _chatTargets.value = ensureServerTargetIsFirstLocal((_chatTargets.value + nick).distinct())
        }
        _activeTarget.value = nick
        if (_allMessages.value[nick] == null) {
            _allMessages.value = _allMessages.value + (nick to emptyList())
        }
        if (_unreadTargets.value.contains(nick)) {
            _unreadTargets.value = _unreadTargets.value - nick
        }
        addSystemMessageToTarget(nick, "Chat privado con $nick iniciado.")
        Log.d("MainViewModel.OpenPM", "PM Opened. activeTarget='${_activeTarget.value}', allMessages keys='${_allMessages.value.keys.joinToString()}', chatTargets='${_chatTargets.value.joinToString()}'")
    }

    fun setActiveTarget(targetName: String) {
        Log.d("MainViewModel.SetActiveTarget", "Attempting to set target: '$targetName'. Current: active='${_activeTarget.value}', chatTargets='${_chatTargets.value.joinToString()}'")
        if (_chatTargets.value.any{it.equals(targetName, ignoreCase = true)} || targetName == SERVER_TARGET_ID) {
            _activeTarget.value = targetName
            if (_unreadTargets.value.contains(targetName)) {
                _unreadTargets.value = _unreadTargets.value - targetName
                Log.d("MainViewModel", "Target '$targetName' marked as read.")
            }
        }
        else {
             Log.w("MainViewModel", "Intento de activar target no existente: $targetName. Targets: ${_chatTargets.value.joinToString()}")
             if (_chatTargets.value.isNotEmpty()) {
                _activeTarget.value = _chatTargets.value.first()
             } else {
                _activeTarget.value = SERVER_TARGET_ID
             }
        }
    }

    fun closeTarget(targetName: String) {
        val currentActiveTarget = _activeTarget.value ?: SERVER_TARGET_ID
        if (targetName == SERVER_TARGET_ID) {
            addSystemMessageToTarget(currentActiveTarget, "La pestaña '$SERVER_TARGET_ID' no se puede cerrar.")
            return
        }
        val wasUnread = _unreadTargets.value.contains(targetName)
        if (targetName.startsWith("#")) {
            Log.d("MainViewModel", "Solicitando PART para el canal: $targetName vía repositorio");
            ircRepository.partChannel(targetName)
        } else {
            _chatTargets.value = ensureServerTargetIsFirstLocal(_chatTargets.value.filterNot { it.equals(targetName, ignoreCase = true) })
            _allMessages.value = _allMessages.value - targetName
            if (wasUnread) {
                _unreadTargets.value = _unreadTargets.value - targetName
            }
            if (_activeTarget.value?.equals(targetName, ignoreCase = true) == true) {
                _activeTarget.value = _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
                _activeTarget.value?.let {
                    if (_unreadTargets.value.contains(it)) {
                        _unreadTargets.value = _unreadTargets.value - it
                    }
                }
            }
        }
    }

    fun sendMessage(messageContent: String) {
        viewModelScope.launch {
            val targetToSend = _activeTarget.value
            if (ircRepository.connectionState.value && !targetToSend.isNullOrBlank() && messageContent.isNotBlank()) { 
                if (targetToSend == SERVER_TARGET_ID) {
                    addSystemMessageToTarget(SERVER_TARGET_ID, "No puedes enviar mensajes a la pestaña '$SERVER_TARGET_ID'. Abre un canal o PM.")
                    return@launch
                }
                val isChannelMessage = targetToSend.startsWith("#")
                val localUiMessage = UiChatMessage(
                    fullText = "<${currentNickname}> $messageContent",
                    type = if (isChannelMessage) UiMessageType.CHANNEL_MSG_SENT else UiMessageType.PRIVATE_MSG_SENT,
                    sender = currentNickname,
                    isOwnMessage = true
                )
                addLocalUiMessageToTarget(targetToSend, localUiMessage)
                ircRepository.sendMessage(targetToSend, messageContent)
            } else {
                Log.w("MainViewModel", "No se puede enviar mensaje. Estado: ${ircRepository.connectionState.value}, Target: $targetToSend, Msg: $messageContent")
                val targetForError = _activeTarget.value ?: _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
                addSystemMessageToTarget(targetForError, "No se puede enviar el mensaje. Verifica la conexión y el target.")
            }
        }
    }

    @Deprecated("Usar disconnectFromServerAndStopService para una desconexión completa.", ReplaceWith("disconnectFromServerAndStopService()"))
    fun disconnect() {
        val targetForMessage = _activeTarget.value ?: _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
        addSystemMessageToTarget(targetForMessage, "Solicitando desconexión del socket IRC...")
        ircRepository.disconnect()
    }

    fun disconnectFromServerAndStopService() {
        Log.i("MainViewModel", "disconnectFromServerAndStopService llamado.")
        val targetForMessage = _activeTarget.value ?: _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
        addSystemMessageToTarget(targetForMessage, "Desconectando y solicitando detener el servicio...")
        viewModelScope.launch {
            _userMessageEvents.emit("Desconectado del servidor.")
        }
        ircRepository.disconnectAndStopService()
    }
}
