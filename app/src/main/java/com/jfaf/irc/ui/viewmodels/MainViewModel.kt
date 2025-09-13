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
    val imageUrl: String? = null
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

// Placeholder para ChatUpdateResult - asegúrate de que coincida con tu definición real
// data class ChatUpdateResult(
// val newCurrentNickname: String? = null,
// val newActiveTarget: String? = null,
// val newAllMessages: Map<String, List<UiChatMessage>>? = null,
// val newChatTargets: List<String>? = null,
// val newUnreadTargets: Set<String>? = null,
// val newUsersInChannel: Map<String, List<String>>? = null,
// val uiMessageToAdd: UiChatMessage? = null,
// val targetForUiMessage: String? = null,
// val privateMessageEventNick: String? = null
// )

@HiltViewModel
class MainViewModel @Inject constructor(
    private val ircRepository: IrcRepository,
    private val ircMessageHandler: IrcMessageHandler,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val chatStateManager: ChatStateManager // << NUEVA DEPENDENCIA INYECTADA
) : ViewModel(), ChatEventListener {

    private var chatEventOrchestrator = ChatEventOrchestrator(
        ircRepository,
        userPreferencesRepository,
        this, // MainViewModel is the ChatEventListener
        viewModelScope
    )

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
    // SERVER_TARGET_ID ahora se accede vía ChatStateManager.SERVER_TARGET_ID

    private val _incomingPrivateMessageEvent = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val incomingPrivateMessageEvent: SharedFlow<String> = _incomingPrivateMessageEvent.asSharedFlow()

    private val _userMessageEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val userMessageEvents: SharedFlow<String> = _userMessageEvents.asSharedFlow()

    // _allMessages sigue siendo gestionado directamente por MainViewModel por ahora
    private val _allMessages = MutableStateFlow<Map<String, List<UiChatMessage>>>(emptyMap())

    // Preferences Flows (sin cambios)
    private val showJoinPartQuitMessagesPref: StateFlow<Boolean> =
        userPreferencesRepository.showJoinPartQuitFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    private val showNickChangesPref: StateFlow<Boolean> =
        userPreferencesRepository.showNickChangesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    private val showModeChangesPref: StateFlow<Boolean> =
        userPreferencesRepository.showModeChangesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    private val showPingPongMessagesPref: StateFlow<Boolean> =
        userPreferencesRepository.showPingPongMessagesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private val ignoredUsersPref: StateFlow<Set<String>> =
        userPreferencesRepository.ignoredUsersFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    @Suppress("UNCHECKED_CAST")
    private val combinedUiMessagesFlow: StateFlow<List<UiChatMessage>> = combine(
        listOf(
            chatStateManager.activeTarget, // << USA EL FLOW DE CHATSTATEMANAGER
            _allMessages,
            showJoinPartQuitMessagesPref,
            showNickChangesPref,
            showModeChangesPref,
            ignoredUsersPref
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val currentChannelUserListState: StateFlow<List<String>> =
        chatStateManager.currentChannelUserListFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val chatScreenState: ChatScreenState = ChatScreenState(
        activeTarget = chatStateManager.activeTarget,
        chatTargets = chatStateManager.chatTargets,
        unreadTargets = chatStateManager.unreadTargets,
        uiMessages = combinedUiMessagesFlow,
        connectionState = ircRepository.connectionState,
        usersInChannel = chatStateManager.usersInChannel,
        currentChannelUserList = currentChannelUserListState,
        showUserList = chatStateManager.showUserList
    )

    init {
        chatEventOrchestrator.startObservingRawMessages()
        ircRepository.connectionState.onEach { isConnected ->
            Log.i("MainViewModel", "Estado de conexión (desde Servicio): ${if (isConnected) "CONECTADO" else "DESCONECTADO"}")
            if (!isConnected) {
                handleServiceDisconnected()
            } else {
                handleServiceConnected()
            }
        }.launchIn(viewModelScope)
    }

    override fun processMessageForUi(parsedMessage: ParsedIrcMessage, ignoredUsersLowercase: Set<String>) {
        if (parsedMessage.command.equals("PING", ignoreCase = true) && !showPingPongMessagesPref.value) {
            Log.d("MainViewModel.processMessageForUi", "PING message received and ignored for UI based on preference.")
            return
        }

        val snapshot = ChatUiSnapshot(
            currentNickname = this.currentNickname,
            activeTarget = chatStateManager.activeTarget.value, 
            allMessages = _allMessages.value, 
            chatTargets = chatStateManager.chatTargets.value, 
            unreadTargets = chatStateManager.unreadTargets.value, 
            usersInChannel = chatStateManager.usersInChannel.value 
        )
        val result = ircMessageHandler.processMessage(snapshot, parsedMessage)
        
        result.newCurrentNickname?.let { this.currentNickname = it }
        result.newAllMessages?.let { _allMessages.value = it } // MainViewModel sigue gestionando _allMessages

        // Delegar actualización de estado de chat a ChatStateManager
        chatStateManager.updateStateFromHandlerResult(result) { this.currentNickname }
        
        Log.d("MainViewModel.processMessageForUi", "Post-update: activeTarget='${chatStateManager.activeTarget.value}', allMessages keys='${_allMessages.value.keys.joinToString()}', chatTargets='${chatStateManager.chatTargets.value.joinToString()}', unread='${chatStateManager.unreadTargets.value.joinToString()}', usersInChannel keys='${chatStateManager.usersInChannel.value.keys.joinToString()}'") 
        
        result.ownNickChangedTo?.let { // Este log puede ser redundante si updateStateFromHandlerResult ya lo maneja
            Log.d("MainViewModel", "Own nick change to '${it}' (via result.ownNickChangedTo) confirmed by IrcMessageHandler.")
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

    fun toggleUserListVisibility() {
        chatStateManager.toggleUserListVisibility()
        Log.d("MainViewModel", "User list visibility toggled to: ${chatStateManager.showUserList.value}")
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

    // ensureServerTargetIsFirstLocal fue eliminado, su lógica está en ChatStateManager

    private fun handleServiceConnected() {
        chatEventOrchestrator.resetSessionState()
        chatStateManager.resetStateForConnection() // Restablece el estado de chat targets, active target, etc.
        
        val serverMessages = _allMessages.value[ChatStateManager.SERVER_TARGET_ID] ?: emptyList()
        val lastMessageText = serverMessages.lastOrNull()?.fullText ?: ""

        // Asegurarse de que el mensaje de "Conectado" solo se añade una vez o si es necesario
        if (chatStateManager.chatTargets.value.isEmpty() || 
            chatStateManager.activeTarget.value == null || 
            !lastMessageText.contains("Conectado al servidor", ignoreCase = true)) {
            // ChatStateManager.resetStateForConnection ya establece el target activo y la lista de targets.
            addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, "Conectado al servidor.")
        }
        Log.i("MainViewModel", "Servicio conectado. Auto-reply list for ignored users has been reset by orchestrator.")
        Log.d("MainViewModel.ServiceConnected", "State: activeTarget='${chatStateManager.activeTarget.value}', allMessages keys='${_allMessages.value.keys.joinToString()}', chatTargets='${chatStateManager.chatTargets.value.joinToString()}'")
    }

    private fun handleServiceDisconnected() {
        val serverMessages = _allMessages.value[ChatStateManager.SERVER_TARGET_ID] ?: emptyList()
        val lastMessageText = serverMessages.lastOrNull()?.fullText ?: ""
        val disconnectMessages = listOf("Desconectado", "Conexión perdida", "El servicio IRC ya no está activo", "Servicio detenido")
        val alreadyHasDisconnectMsg = disconnectMessages.any { lastMessageText.contains(it, ignoreCase = true) }
        val messageText = when {
            lastMessageText.contains("Servicio detenido", ignoreCase = true) -> "Desconectado. El servicio fue detenido."
            alreadyHasDisconnectMsg -> null
            else -> "Desconectado. El servicio IRC perdió la conexión o fue detenido."
        }
        if (messageText != null) {
            addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, messageText)
        }
        
        chatStateManager.resetStateForDisconnection() // Resetea targets, active target, users, etc.

        val newAllMessages = mutableMapOf<String, List<UiChatMessage>>()
        _allMessages.value[ChatStateManager.SERVER_TARGET_ID]?.let { newAllMessages[ChatStateManager.SERVER_TARGET_ID] = it }
        _allMessages.value = newAllMessages.toMap() // Mantiene solo los mensajes del servidor
        
        Log.i("MainViewModel", "UI actualizada para reflejar desconexión del servicio. usersInChannel reseteado.")
    }

    fun connect(nickname: String, ssl: Boolean) {
        this.currentNickname = nickname
        val hostToConnect = defaultHost; val portToConnect = if (ssl) 6697 else 6667
        Log.d("MainViewModel", "connect: Nick: $nickname, Host: $hostToConnect, Port: $portToConnect, SSL: $ssl")
        
        chatStateManager.resetStateForConnection()
        _allMessages.value = mapOf(ChatStateManager.SERVER_TARGET_ID to emptyList()) // Resetea todos los mensajes
        
        Log.d("MainViewModel.Connect", "Post-init: activeTarget='${chatStateManager.activeTarget.value}', allMessages keys='${_allMessages.value.keys.joinToString()}', chatTargets='${chatStateManager.chatTargets.value.joinToString()}', usersInChannel keys='${chatStateManager.usersInChannel.value.keys.joinToString()}'") 
        addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, "Conectando a $hostToConnect como $nickname...")
        ircRepository.connect(hostToConnect, portToConnect, ssl, nickname)
    }

    fun joinChannel(channelName: String) {
        val currentActiveTarget = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID
        if (!channelName.startsWith("#")) {
            addSystemMessageToTarget(currentActiveTarget, "Nombre de canal inválido: $channelName. Debe empezar con #.")
            return
        }
        if (ircRepository.connectionState.value && channelName.isNotBlank()) { 
            addSystemMessageToTarget(currentActiveTarget, "Solicitando unirse a $channelName...")
            // ChatStateManager añadirá el target cuando el JOIN sea confirmado por el servidor via IrcMessageHandler
            ircRepository.joinChannel(channelName)
        } else {
            Log.w("MainViewModel", "joinChannel: No conectado o channelName vacío.")
            addSystemMessageToTarget(currentActiveTarget, "No se puede unir al canal. No conectado.")
        }
    }

    fun openPrivateMessage(nick: String) {
        Log.d("MainViewModel.OpenPM", "Attempting to open PM with: '$nick'. Current: active='${chatStateManager.activeTarget.value}', chatTargets='${chatStateManager.chatTargets.value.joinToString()}'")
        val currentActiveTargetForErrorMsg = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID

        if (nick.isBlank() || nick.startsWith("#") || nick.equals(currentNickname, ignoreCase = true)) {
            addSystemMessageToTarget(currentActiveTargetForErrorMsg, "Nombre de usuario inválido para chat privado: $nick")
            return
        }
        
        chatStateManager.openPrivateMessageTarget(nick, this.currentNickname)
        // Asegurar que haya una lista de mensajes para el nuevo target de PM
        if (_allMessages.value[nick] == null) {
            _allMessages.value = _allMessages.value + (nick to emptyList())
        }
        addSystemMessageToTarget(nick, "Chat privado con $nick iniciado.")
        Log.d("MainViewModel.OpenPM", "PM Opened. activeTarget='${chatStateManager.activeTarget.value}', allMessages keys='${_allMessages.value.keys.joinToString()}', chatTargets='${chatStateManager.chatTargets.value.joinToString()}'")
    }

    fun setActiveTarget(targetName: String) {
        Log.d("MainViewModel.SetActiveTarget", "Attempting to set target: '$targetName'. Current: active='${chatStateManager.activeTarget.value}', chatTargets='${chatStateManager.chatTargets.value.joinToString()}'")
        chatStateManager.setActiveTarget(targetName, this.currentNickname)
    }

    fun closeTarget(targetName: String) {
        val currentActiveForMsg = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID
        if (targetName == ChatStateManager.SERVER_TARGET_ID) {
            addSystemMessageToTarget(currentActiveForMsg, "La pestaña '${ChatStateManager.SERVER_TARGET_ID}' no se puede cerrar.")
            return
        }
        
        // Si es un canal, enviar PART. El estado se actualizará cuando el servidor confirme.
        if (targetName.startsWith("#")) {
            Log.d("MainViewModel", "Solicitando PART para el canal: $targetName vía repositorio");
            ircRepository.partChannel(targetName)
            // Marcar como leído si estaba activo y no leído
            if (chatStateManager.unreadTargets.value.contains(targetName) && chatStateManager.activeTarget.value == targetName) {
                 chatStateManager.setActiveTarget(targetName, this.currentNickname) // Esto lo marcará como leído
            }
        } else { // Si es un PM, el cierre es solo local y actualiza el estado inmediatamente.
            val newActiveTarget = chatStateManager.closeTarget(targetName, chatStateManager.activeTarget.value, this.currentNickname)
            // Eliminar mensajes del target cerrado (para PMs)
            _allMessages.value = _allMessages.value - targetName
            // No es necesario actualizar _activeTarget aquí explícitamente si ChatStateManager ya lo hizo,
            // pero si newActiveTarget es diferente, la UI se recompone por el cambio en el StateFlow.
        }
    }

    fun sendMessage(messageContent: String) {
        viewModelScope.launch {
            val targetToSend = chatStateManager.activeTarget.value
            if (ircRepository.connectionState.value && !targetToSend.isNullOrBlank() && messageContent.isNotBlank()) { 
                if (targetToSend == ChatStateManager.SERVER_TARGET_ID) {
                    addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, "No puedes enviar mensajes a la pestaña '${ChatStateManager.SERVER_TARGET_ID}'. Abre un canal o PM.")
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
                val targetForError = chatStateManager.activeTarget.value ?: chatStateManager.chatTargets.value.firstOrNull() ?: ChatStateManager.SERVER_TARGET_ID
                addSystemMessageToTarget(targetForError, "No se puede enviar el mensaje. Verifica la conexión y el target.")
            }
        }
    }

    @Deprecated("Usar disconnectFromServerAndStopService para una desconexión completa.", ReplaceWith("disconnectFromServerAndStopService()"))
    fun disconnect() {
        val targetForMessage = chatStateManager.activeTarget.value ?: chatStateManager.chatTargets.value.firstOrNull() ?: ChatStateManager.SERVER_TARGET_ID
        addSystemMessageToTarget(targetForMessage, "Solicitando desconexión del socket IRC...")
        ircRepository.disconnect()
    }

    fun disconnectFromServerAndStopService() {
        Log.i("MainViewModel", "disconnectFromServerAndStopService llamado.")
        val targetForMessage = chatStateManager.activeTarget.value ?: chatStateManager.chatTargets.value.firstOrNull() ?: ChatStateManager.SERVER_TARGET_ID
        addSystemMessageToTarget(targetForMessage, "Desconectando y solicitando detener el servicio...")
        viewModelScope.launch {
            _userMessageEvents.emit("Desconectado del servidor.")
        }
        ircRepository.disconnectAndStopService()
        // El estado de la UI (targets, etc.) se actualiza a través de la observación de connectionState -> handleServiceDisconnected
    }
}
