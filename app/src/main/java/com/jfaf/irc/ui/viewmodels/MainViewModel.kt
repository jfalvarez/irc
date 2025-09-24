package com.jfaf.irc.ui.viewmodels

import android.os.Bundle // Import Bundle for Firebase Analytics
import android.util.Log
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics // Import FirebaseAnalytics
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

data class UiChatMessage(
    val fullText: String, 
    val annotatedString: AnnotatedString? = null, 
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

data class ChatScreenState(
    val activeTarget: StateFlow<String?>,
    val chatTargets: StateFlow<List<String>>,
    val unreadTargets: StateFlow<Set<String>>,
    val uiMessages: StateFlow<List<UiChatMessage>>,
    val connectionState: StateFlow<Boolean>,
    val usersInChannel: StateFlow<Map<String, List<String>>>,
    val currentChannelUserList: StateFlow<List<String>>,
    val showUserList: StateFlow<Boolean>,
    val nickSuggestions: StateFlow<List<String>>
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val ircRepository: IrcRepository,
    private val ircMessageHandler: IrcMessageHandler,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val chatStateManager: ChatStateManager,
    private val firebaseAnalytics: FirebaseAnalytics // Inyectar FirebaseAnalytics
) : ViewModel(), ChatEventListener {

    private var chatEventOrchestrator = ChatEventOrchestrator(
        ircRepository,
        userPreferencesRepository,
        this, 
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

    private val _incomingPrivateMessageEvent = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val incomingPrivateMessageEvent: SharedFlow<String> = _incomingPrivateMessageEvent.asSharedFlow()

    private val _userMessageEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val userMessageEvents: SharedFlow<String> = _userMessageEvents.asSharedFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _nickSuggestions = MutableStateFlow<List<String>>(emptyList())

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
            chatStateManager.activeTarget, 
            chatStateManager.allMessages, 
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
                    (message.annotatedString?.text?.contains("signed off", ignoreCase = true) == true) ||
                    message.fullText.contains("connection closed", ignoreCase = true) ||
                    (message.annotatedString?.text?.contains("connection closed", ignoreCase = true) == true)) {
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
        showUserList = chatStateManager.showUserList,
        nickSuggestions = _nickSuggestions.asStateFlow()
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
            allMessages = chatStateManager.allMessages.value, 
            chatTargets = chatStateManager.chatTargets.value, 
            unreadTargets = chatStateManager.unreadTargets.value, 
            usersInChannel = chatStateManager.usersInChannel.value 
        )
        val result = ircMessageHandler.processMessage(snapshot, parsedMessage)
        
        result.newCurrentNickname?.let { this.currentNickname = it }

        chatStateManager.updateStateFromHandlerResult(result) { this.currentNickname }
        
        result.ownNickChangedTo?.let { 
            Log.d("MainViewModel", "Own nick change to '${it}' (via result.ownNickChangedTo) confirmed by IrcMessageHandler.")
        }
        result.privateMessageEventNick?.let { nick ->
            if (nick.lowercase() !in ignoredUsersLowercase) { 
                emitPrivateMessageEvent(nick) 
            } else {
                Log.d("MainViewModel", "PM Event for '$nick' from IrcMessageHandler suppressed as user is in ignored list: ${ignoredUsersLowercase.joinToString()}")
            }
        }
    }

    override fun emitPrivateMessageEvent(nick: String) {
        val currentIgnoredUsers = ignoredUsersPref.value.map { it.lowercase() }.toSet()
        if (nick.lowercase() !in currentIgnoredUsers) {
            _incomingPrivateMessageEvent.tryEmit(nick)
        } else {
            Log.d("MainViewModel", "PM Event for '$nick' (from orchestrator) suppressed as user is in current ignored list.")
        }
    }

    override fun isConnected(): Boolean = ircRepository.connectionState.value

    fun toggleUserListVisibility() {
        chatStateManager.toggleUserListVisibility()
    }

    private fun handleServiceConnected() {
        chatEventOrchestrator.resetSessionState()
        chatStateManager.resetStateForConnection() 
        
        val serverMessages = chatStateManager.allMessages.value[ChatStateManager.SERVER_TARGET_ID] ?: emptyList()
        val lastMessageText = serverMessages.lastOrNull()?.let { it.annotatedString?.text ?: it.fullText } ?: ""

        if (chatStateManager.chatTargets.value.isEmpty() || 
            chatStateManager.activeTarget.value == null || 
            !lastMessageText.contains("Conectado al servidor", ignoreCase = true)) {
            chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, "Conectado al servidor.")
        }
    }

    private fun handleServiceDisconnected() {
        val serverMessages = chatStateManager.allMessages.value[ChatStateManager.SERVER_TARGET_ID] ?: emptyList()
        val lastMessageText = serverMessages.lastOrNull()?.let { it.annotatedString?.text ?: it.fullText } ?: ""
        val disconnectMessages = listOf("Desconectado", "Conexión perdida", "El servicio IRC ya no está activo", "Servicio detenido")
        val alreadyHasDisconnectMsg = disconnectMessages.any { lastMessageText.contains(it, ignoreCase = true) }
        val messageText = when {
            lastMessageText.contains("Servicio detenido", ignoreCase = true) -> "Desconectado. El servicio fue detenido."
            alreadyHasDisconnectMsg -> null
            else -> "Desconectado. El servicio IRC perdió la conexión o fue detenido."
        }
        if (messageText != null) {
            chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, messageText)
        }
        
        chatStateManager.resetStateForDisconnection() 
    }

    fun connect(nickname: String, ssl: Boolean) {
        this.currentNickname = nickname
        val hostToConnect = defaultHost; val portToConnect = if (ssl) 6697 else 6667
        
        chatStateManager.resetStateForConnection() 
        
        chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, "Conectando a $hostToConnect como $nickname...")
        ircRepository.connect(hostToConnect, portToConnect, ssl, nickname)
    }

    fun joinChannel(channelName: String) {
        val currentActiveTarget = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID
        if (!channelName.startsWith("#")) {
            chatStateManager.addSystemMessageToTarget(currentActiveTarget, "Nombre de canal inválido: $channelName. Debe empezar con #.")
            return
        }
        if (ircRepository.connectionState.value && channelName.isNotBlank()) { 
            chatStateManager.addSystemMessageToTarget(currentActiveTarget, "Solicitando unirse a $channelName...")
            ircRepository.joinChannel(channelName)
        } else {
            chatStateManager.addSystemMessageToTarget(currentActiveTarget, "No se puede unir al canal. No conectado.")
        }
    }

    fun openPrivateMessage(nick: String) {
        val currentActiveTargetForErrorMsg = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID

        if (nick.isBlank() || nick.startsWith("#") || nick.equals(currentNickname, ignoreCase = true)) {
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForErrorMsg, "Nombre de usuario inválido para chat privado: $nick")
            return
        }
        
        chatStateManager.openPrivateMessageTarget(nick, this.currentNickname)
        chatStateManager.addSystemMessageToTarget(nick, "Chat privado con $nick iniciado.")
    }

    fun setActiveTarget(targetName: String) {
        chatStateManager.setActiveTarget(targetName, this.currentNickname)
        // Clear suggestions when changing target
        clearNickSuggestions()
    }

    fun closeTarget(targetName: String) {
        val currentActiveForMsg = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID
        if (targetName == ChatStateManager.SERVER_TARGET_ID) {
            chatStateManager.addSystemMessageToTarget(currentActiveForMsg, "La pestaña '${ChatStateManager.SERVER_TARGET_ID}' no se puede cerrar.")
            return
        }
        
        if (targetName.startsWith("#")) {
            ircRepository.partChannel(targetName) 
            if (chatStateManager.unreadTargets.value.contains(targetName) && chatStateManager.activeTarget.value == targetName) {
                 chatStateManager.setActiveTarget(targetName, this.currentNickname) 
            }
        } else {
            chatStateManager.closeTarget(targetName, chatStateManager.activeTarget.value, this.currentNickname)
        }
        // Clear suggestions if the closed target was the active one
        if (chatStateManager.activeTarget.value == targetName) {
            clearNickSuggestions()
        }
    }

    fun sendMessage(messageContent: String) {
        viewModelScope.launch {
            val targetToSend = chatStateManager.activeTarget.value
            if (ircRepository.connectionState.value && !targetToSend.isNullOrBlank() && messageContent.isNotBlank()) { 
                if (targetToSend == ChatStateManager.SERVER_TARGET_ID) {
                    chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, "No puedes enviar mensajes a la pestaña '${ChatStateManager.SERVER_TARGET_ID}'. Abre un canal o PM.")
                    return@launch
                }
                val isChannelMessage = targetToSend.startsWith("#")
                val localUiMessage = UiChatMessage(
                    fullText = "<${currentNickname}> $messageContent",
                    annotatedString = AnnotatedString("<${currentNickname}> $messageContent"), 
                    type = if (isChannelMessage) UiMessageType.CHANNEL_MSG_SENT else UiMessageType.PRIVATE_MSG_SENT,
                    sender = currentNickname,
                    isOwnMessage = true
                )
                chatStateManager.addLocalUiMessageToTarget(targetToSend, localUiMessage)
                ircRepository.sendMessage(targetToSend, messageContent)
                clearNickSuggestions() // Clear suggestions after sending a message
            } else {
                val targetForError = chatStateManager.activeTarget.value ?: chatStateManager.chatTargets.value.firstOrNull() ?: ChatStateManager.SERVER_TARGET_ID
                chatStateManager.addSystemMessageToTarget(targetForError, "No se puede enviar el mensaje. Verifica la conexión y el target.")
            }
        }
    }

    @Deprecated("Usar disconnectFromServerAndStopService para una desconexión completa.", ReplaceWith("disconnectFromServerAndStopService()"))
    fun disconnect() {
        val targetForMessage = chatStateManager.activeTarget.value ?: chatStateManager.chatTargets.value.firstOrNull() ?: ChatStateManager.SERVER_TARGET_ID
        chatStateManager.addSystemMessageToTarget(targetForMessage, "Solicitando desconexión del socket IRC...")
        ircRepository.disconnect()
    }

    fun disconnectFromServerAndStopService() {
        val targetForMessage = chatStateManager.activeTarget.value ?: chatStateManager.chatTargets.value.firstOrNull() ?: ChatStateManager.SERVER_TARGET_ID
        chatStateManager.addSystemMessageToTarget(targetForMessage, "Desconectando y solicitando detener el servicio...")
        viewModelScope.launch {
            _userMessageEvents.emit("Desconectado del servidor.")
        }
        ircRepository.disconnectAndStopService()
        clearNickSuggestions()
    }

    fun ignoreUser(userName: String) {
        viewModelScope.launch {
            userPreferencesRepository.addIgnoredUser(userName)
            val targetForMessage = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID
            chatStateManager.addSystemMessageToTarget(targetForMessage, "Usuario '$userName' ahora está en la lista de ignorados.")
            _snackbarEvents.tryEmit("Usuario '$userName' añadido a ignorados.") // Snackbar para confirmar acción
            firebaseAnalytics.logEvent("ignore_user", null)
        }
    }

    fun performWhois(nick: String) {
        if (nick.isBlank()) {
            Log.w("MainViewModel", "performWhois llamado con nick vacío.")
            return
        }
        // Registrar evento de Analytics ANTES de las comprobaciones de conexión
        // para capturar la intención del usuario incluso si la acción no se completa.
        val bundle = Bundle()
        bundle.putString("whois_target_nick", nick) // Opcional: añadir parámetro con el nick
        firebaseAnalytics.logEvent("whois_request", bundle)

        if (!ircRepository.connectionState.value) {
            Log.w("MainViewModel", "performWhois llamado pero no conectado al servidor.")
            chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, "No conectado. No se puede enviar WHOIS.")
            _snackbarEvents.tryEmit("Error: No conectado al servidor para enviar WHOIS.")
            return
        }
        Log.i("MainViewModel", "Enviando comando WHOIS para $nick")
        chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, "[WHOIS] Solicitando información para $nick...")
        ircRepository.sendRawCommand("WHOIS $nick")
        _snackbarEvents.tryEmit("WHOIS para '$nick' solicitado. Ver pestaña 'Servidor'.") 
    }

    // --- Nick Autocompletion Functions ---
    fun updateNickSuggestions(currentFullText: String, cursorPosition: Int) {
        val activeTargetValue = chatStateManager.activeTarget.value
        if (activeTargetValue == null || !activeTargetValue.startsWith("#")) {
            _nickSuggestions.value = emptyList()
            return
        }

        // Extract the word being typed at the cursor position
        val textUpToCursor = currentFullText.substring(0, cursorPosition)
        val lastWordStartIndex = textUpToCursor.lastIndexOf(' ') + 1
        val currentWord = textUpToCursor.substring(lastWordStartIndex)

        if (currentWord.length < 2) { // Minimum length for suggestion
            _nickSuggestions.value = emptyList()
            return
        }

        val usersInCurrentChannel = currentChannelUserListState.value
        _nickSuggestions.value = usersInCurrentChannel.filter {
            it.startsWith(currentWord, ignoreCase = true) && 
            it.length > currentWord.length // Only suggest if different
        }.take(5) // Limit to 5 suggestions
    }

    fun clearNickSuggestions() {
        _nickSuggestions.value = emptyList()
    }

    fun onNickSuggestionSelected(suggestion: String, currentFullText: String, cursorPosition: Int): String {
        val textUpToCursor = currentFullText.substring(0, cursorPosition)
        val lastSpaceIndex = textUpToCursor.lastIndexOf(' ')
        val prefix = if (lastSpaceIndex == -1) "" else currentFullText.substring(0, lastSpaceIndex + 1)
        val suffix = currentFullText.substring(cursorPosition)
        
        val newText = "$prefix$suggestion $suffix" // Add a space after suggestion
        clearNickSuggestions()
        return newText.trimEnd() + if(newText.length > (prefix.length + suggestion.length)) "" else " " // Ensure space at end for next word
    }
}
