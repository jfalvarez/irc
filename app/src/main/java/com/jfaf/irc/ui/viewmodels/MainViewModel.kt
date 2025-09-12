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
    val isOwnMessage: Boolean = false
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
// --- End of Data classes ---

@HiltViewModel
class MainViewModel @Inject constructor(
    private val ircRepository: IrcRepository,
    private val ircMessageHandler: IrcMessageHandler,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val connectionState: StateFlow<Boolean> = ircRepository.connectionState
    val rawIrcMessagesEvents: SharedFlow<ParsedIrcMessage> = ircRepository.incomingMessages

    var currentNickname = "IrcUser${(100..999).random()}"
        private set
    private val defaultHost = "irc.irc-hispano.org"
    private val SERVER_TARGET_ID = "Servidor"

    private val _incomingPrivateMessageEvent = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val incomingPrivateMessageEvent: SharedFlow<String> = _incomingPrivateMessageEvent.asSharedFlow()

    private val _userMessageEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val userMessageEvents: SharedFlow<String> = _userMessageEvents.asSharedFlow()

    private val _unreadTargets = MutableStateFlow<Set<String>>(emptySet())
    val unreadTargets: StateFlow<Set<String>> = _unreadTargets.asStateFlow()

    private val _chatTargets = MutableStateFlow<List<String>>(emptyList())
    val chatTargets: StateFlow<List<String>> = _chatTargets.asStateFlow()

    private val _activeTarget = MutableStateFlow<String?>(null)
    val activeTarget: StateFlow<String?> = _activeTarget.asStateFlow()

    private val _allMessages = MutableStateFlow<Map<String, List<UiChatMessage>>>(emptyMap())

    // Preferences Flows
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

    val uiMessages: StateFlow<List<UiChatMessage>> = combine(
        _activeTarget,
        _allMessages,
        showJoinPartQuitMessagesPref,
        showNickChangesPref,
        showModeChangesPref
    ) { active, allMsgs, showJpq, showNick, showMode ->
        val messagesForTarget = allMsgs[active] ?: emptyList()
        
        messagesForTarget.filter { message ->
            var shouldShow = true // Assume message should be shown by default

            // Filter based on JOIN/PART/QUIT and specific text content
            if (!showJpq) {
                if (message.type == UiMessageType.JOIN_PART_QUIT ||
                    message.fullText.contains("signed off", ignoreCase = true) ||
                    message.fullText.contains("connection closed", ignoreCase = true)) {
                    shouldShow = false
                }
            }

            // Filter based on Nick Changes, only if not already hidden
            if (shouldShow && !showNick) {
                if (message.type == UiMessageType.NICK_CHANGE) {
                    shouldShow = false
                }
            }

            // Filter based on Mode Changes, only if not already hidden
            if (shouldShow && !showMode) {
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

    init {
        rawIrcMessagesEvents
            .onEach { parsedMessage ->
                Log.d("MainViewModel", "Internal Raw In (from Service via Repo): $parsedMessage. Delegating to IrcMessageHandler.")
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

    private fun processIncomingParsedMessage(parsedMessage: ParsedIrcMessage) {
        val snapshot = ChatUiSnapshot(
            currentNickname = this.currentNickname,
            activeTarget = _activeTarget.value,
            allMessages = _allMessages.value,
            chatTargets = _chatTargets.value,
            unreadTargets = _unreadTargets.value
        )

        val result = ircMessageHandler.processMessage(snapshot, parsedMessage)

        this.currentNickname = result.newCurrentNickname
        _activeTarget.value = result.newActiveTarget
        _allMessages.value = result.newAllMessages // _allMessages still stores ALL messages
        _chatTargets.value = result.newChatTargets
        _unreadTargets.value = result.newUnreadTargets
        Log.d("MainViewModel.ProcessResult", "Post-update: activeTarget='${_activeTarget.value}', allMessages keys='${_allMessages.value.keys.joinToString()}', chatTargets='${_chatTargets.value.joinToString()}', unread='${_unreadTargets.value.joinToString()}'")

        result.ownNickChangedTo?.let {
            Log.d("MainViewModel", "Own nick change to '${it}' confirmed by IrcMessageHandler.")
        }

        result.privateMessageEventNick?.let {
            _incomingPrivateMessageEvent.tryEmit(it)
            Log.d("MainViewModel", "PM Event for '$it' emitted via IrcMessageHandler result.")
        }

        if (result.uiMessageToAdd != null && result.targetForUiMessage != null) {
            Log.d("MainViewModel", "IrcMessageHandler result for ${parsedMessage.command} included a direct UiMessage for [${result.targetForUiMessage}]: ${result.uiMessageToAdd}. ViewModel state updated via newAllMessages.")
        } else {
            Log.d("MainViewModel", "IrcMessageHandler processed ${parsedMessage.command}. ViewModel state updated from result.")
        }
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
        val serverMessages = _allMessages.value[SERVER_TARGET_ID] ?: emptyList()
        val lastMessageText = serverMessages.lastOrNull()?.fullText ?: ""

        if (_chatTargets.value.isEmpty() || _activeTarget.value == null || !lastMessageText.contains("Conectado al servidor", ignoreCase = true)) {
            _chatTargets.value = ensureServerTargetIsFirstLocal((_chatTargets.value + SERVER_TARGET_ID).distinct())
            _activeTarget.value = _activeTarget.value ?: SERVER_TARGET_ID
            addSystemMessageToTarget(SERVER_TARGET_ID, "Conectado al servidor.")
        }
        Log.i("MainViewModel", "Servicio conectado.")
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

        Log.i("MainViewModel", "UI actualizada para reflejar desconexión del servicio.")
    }

    fun connect(nickname: String, ssl: Boolean) {
        this.currentNickname = nickname
        val hostToConnect = defaultHost; val portToConnect = if (ssl) 6697 else 6667
        Log.d("MainViewModel", "connect: Nick: $nickname, Host: $hostToConnect, Port: $portToConnect, SSL: $ssl")

        _chatTargets.value = ensureServerTargetIsFirstLocal(listOf(SERVER_TARGET_ID))
        _activeTarget.value = SERVER_TARGET_ID
        _allMessages.value = mapOf(SERVER_TARGET_ID to emptyList())
        _unreadTargets.value = emptySet()
        Log.d("MainViewModel.Connect", "Post-init: activeTarget='${_activeTarget.value}', allMessages keys='${_allMessages.value.keys.joinToString()}', chatTargets='${_chatTargets.value.joinToString()}'")
        addSystemMessageToTarget(SERVER_TARGET_ID, "Conectando a $hostToConnect como $nickname...")
        ircRepository.connect(hostToConnect, portToConnect, ssl, nickname)
    }

    fun joinChannel(channelName: String) {
        val currentActiveTarget = _activeTarget.value ?: SERVER_TARGET_ID
        if (!channelName.startsWith("#")) {
            addSystemMessageToTarget(currentActiveTarget, "Nombre de canal inválido: $channelName. Debe empezar con #.")
            return
        }
        if (connectionState.value && channelName.isNotBlank()) {
            addSystemMessageToTarget(currentActiveTarget, "Solicitando unirse a $channelName...")
            ircRepository.joinChannel(channelName)
        } else {
            Log.w("MainViewModel", "joinChannel: No conectado o channelName vacío.")
            addSystemMessageToTarget(currentActiveTarget, "No se puede unir al canal. No conectado.")
        }
    }

    fun openPrivateMessage(nick: String) {
        Log.d("MainViewModel.SetActiveTarget", "Attempting to open PM with: '$nick'. Current: active='${_activeTarget.value}', chatTargets='${_chatTargets.value.joinToString()}'") // Combined log from setActiveTarget logic
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
            if (connectionState.value && !targetToSend.isNullOrBlank() && messageContent.isNotBlank()) {
                if (targetToSend == SERVER_TARGET_ID) {
                    addSystemMessageToTarget(SERVER_TARGET_ID, "No puedes enviar mensajes a la pestaña '$SERVER_TARGET_ID'. Abre un canal o PM.")
                    return@launch
                }

                // --- START: Added for Local Echo ---
                val isChannelMessage = targetToSend.startsWith("#")
                val localUiMessage = UiChatMessage(
                    fullText = "<${currentNickname}> $messageContent",
                    type = if (isChannelMessage) UiMessageType.CHANNEL_MSG_SENT else UiMessageType.PRIVATE_MSG_SENT,
                    sender = currentNickname,
                    isOwnMessage = true
                )
                addLocalUiMessageToTarget(targetToSend, localUiMessage)
                // --- END: Added for Local Echo ---

                ircRepository.sendMessage(targetToSend, messageContent)
            } else {
                Log.w("MainViewModel", "No se puede enviar mensaje. Estado: ${connectionState.value}, Target: $targetToSend, Msg: $messageContent")
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
