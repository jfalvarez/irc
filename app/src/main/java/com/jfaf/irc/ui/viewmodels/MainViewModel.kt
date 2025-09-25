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
    ACTION_MSG, // Para /me
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
        val ignoredUsersLowercase: Set<String> // Pre-calcular a lowercase
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

    private fun shouldDisplayMessage(message: UiChatMessage, filterContext: UiMessagesFilterContext): Boolean {
        if (message.sender != null &&
            (message.type == UiMessageType.CHANNEL_MSG_RECEIVED || 
             message.type == UiMessageType.PRIVATE_MSG_RECEIVED || 
             message.type == UiMessageType.ACTION_MSG) &&
            message.sender.lowercase() in filterContext.ignoredUsersLowercase) {
            return false // Ignorar mensaje de usuario ignorado
        }

        if (!filterContext.showJpq && 
            (message.type == UiMessageType.JOIN_PART_QUIT ||
             message.fullText.contains("signed off", ignoreCase = true) || 
             (message.annotatedString?.text?.contains("signed off", ignoreCase = true) == true) ||
             message.fullText.contains("connection closed", ignoreCase = true) ||
             (message.annotatedString?.text?.contains("connection closed", ignoreCase = true) == true))) {
            return false // Ocultar mensajes JPQ si la preferencia está desactivada
        }

        if (!filterContext.showNick && message.type == UiMessageType.NICK_CHANGE) {
            return false // Ocultar cambios de nick si la preferencia está desactivada
        }

        if (!filterContext.showMode && message.type == UiMessageType.MODE_CHANGE) {
            return false // Ocultar cambios de modo si la preferencia está desactivada
        }

        return true // Mostrar el mensaje por defecto
    }

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
            ignoredUsersLowercase = (values[5] as Set<String>).map { it.lowercase() }.toSet()
        )
        val messagesForTarget = filterContext.allMessages[filterContext.activeTarget] ?: emptyList()
        messagesForTarget.filter { message -> shouldDisplayMessage(message, filterContext) }
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

    fun joinChannel(channelName: String, key: String? = null) {
        val currentActiveTargetForMsg = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID
        if (!channelName.startsWith("#")) {
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForMsg, "Nombre de canal inválido: $channelName. Debe empezar con #.")
            return
        }
        if (ircRepository.connectionState.value && channelName.isNotBlank()) { 
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForMsg, "Solicitando unirse a $channelName...")
            ircRepository.joinChannel(channelName, key)
        } else {
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForMsg, "No se puede unir al canal. No conectado.")
        }
    }

    fun openPrivateMessage(nick: String) {
        val currentActiveTargetForErrorMsg = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID

        if (nick.isBlank() || nick.startsWith("#") || nick.equals(currentNickname, ignoreCase = true)) {
            chatStateManager.addSystemMessageToTarget(currentActiveTargetForErrorMsg, "Nombre de usuario inválido para chat privado: $nick")
            return
        }
        
        chatStateManager.openPrivateMessageTarget(nick, this.currentNickname)
        if (chatStateManager.activeTarget.value == nick) { 
            chatStateManager.addSystemMessageToTarget(nick, "Chat privado con $nick iniciado.")
        }
    }

    fun setActiveTarget(targetName: String) {
        chatStateManager.setActiveTarget(targetName, this.currentNickname)
        clearNickSuggestions()
    }

    fun partChannel(channelName: String?, partMessage: String? = null) {
        val targetToPart = channelName ?: chatStateManager.activeTarget.value
        if (targetToPart == null || !targetToPart.startsWith("#")) {
            chatStateManager.addSystemMessageToTarget(
                chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID,
                "No se puede salir: no es un canal válido o no hay canal activo."
            )
            return
        }
        ircRepository.partChannel(targetToPart, partMessage)
    }

    fun closeTarget(targetName: String) {
        val currentActiveForMsg = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID
        if (targetName == ChatStateManager.SERVER_TARGET_ID) {
            chatStateManager.addSystemMessageToTarget(currentActiveForMsg, "La pestaña '${ChatStateManager.SERVER_TARGET_ID}' no se puede cerrar.")
            return
        }
        
        if (targetName.startsWith("#")) {
            partChannel(targetName) 
        } else {
            val newActive = chatStateManager.closeTarget(targetName, chatStateManager.activeTarget.value, this.currentNickname)
        }
        if (chatStateManager.activeTarget.value == targetName) { 
            clearNickSuggestions()
        }
    }

    fun sendMessage(messageContent: String) {
        viewModelScope.launch {
            val currentActiveTarget = chatStateManager.activeTarget.value

            if (!ircRepository.connectionState.value) {
                chatStateManager.addSystemMessageToTarget(
                    currentActiveTarget ?: ChatStateManager.SERVER_TARGET_ID,
                    "No conectado. No se puede enviar el mensaje/comando."
                )
                return@launch
            }
            if (messageContent.isBlank()) {
                return@launch 
            }

            if (messageContent.startsWith("/")) {
                handleCommand(messageContent, currentActiveTarget)
            } else {
                if (currentActiveTarget == ChatStateManager.SERVER_TARGET_ID) {
                    chatStateManager.addSystemMessageToTarget(
                        ChatStateManager.SERVER_TARGET_ID,
                        "No puedes enviar mensajes a la pestaña '${ChatStateManager.SERVER_TARGET_ID}'. Utiliza comandos (ej: /join, /nick) o abre un canal/PM."
                    )
                } else if (currentActiveTarget.isNullOrBlank()) {
                    chatStateManager.addSystemMessageToTarget(
                        ChatStateManager.SERVER_TARGET_ID,
                        "No hay un target activo para enviar el mensaje."
                    )
                } else {
                    val isChannelMessage = currentActiveTarget.startsWith("#")
                    val uiMessage = UiChatMessage(
                        fullText = "<${currentNickname}> $messageContent",
                        annotatedString = AnnotatedString("<${currentNickname}> $messageContent"),
                        type = if (isChannelMessage) UiMessageType.CHANNEL_MSG_SENT else UiMessageType.PRIVATE_MSG_SENT,
                        sender = currentNickname,
                        isOwnMessage = true
                    )
                    chatStateManager.addLocalUiMessageToTarget(currentActiveTarget, uiMessage)
                    ircRepository.sendMessage(currentActiveTarget, messageContent)
                    clearNickSuggestions()
                }
            }
        }
    }

    // --- Command Handling --- 
    private fun parseCommandAndArgs(commandLine: String): Pair<String, String?> {
        val commandAndArgsString = commandLine.drop(1).trim()
        val parts = commandAndArgsString.split(" ", limit = 2)
        val command = parts[0].lowercase()
        val args = parts.getOrNull(1)
        return command to args
    }

    private fun handleCommand(commandLine: String, currentActiveTarget: String?) {
        val (command, args) = parseCommandAndArgs(commandLine)
        val targetForSysMsgOnError = currentActiveTarget ?: ChatStateManager.SERVER_TARGET_ID

        if (command.isNullOrBlank()) { // Chequeo cambiado a isNullOrBlank para el comando parseado
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "Comando inválido.")
            return
        }

        when (command) {
            "me" -> executeMeCommand(args, currentActiveTarget, targetForSysMsgOnError)
            "nick" -> executeNickCommand(args, targetForSysMsgOnError)
            "join" -> executeJoinCommand(args, targetForSysMsgOnError)
            "part" -> executePartCommand(args, currentActiveTarget, targetForSysMsgOnError)
            "quit" -> executeQuitCommand(args)
            "away" -> executeAwayCommand(args, targetForSysMsgOnError)
            "msg" -> executeMsgCommand(args, targetForSysMsgOnError)
            "query" -> executeQueryCommand(args, targetForSysMsgOnError)
            "topic" -> executeTopicCommand(args, currentActiveTarget, targetForSysMsgOnError)
            "clear" -> executeClearCommand(currentActiveTarget, targetForSysMsgOnError)
            "help" -> executeHelpCommand(targetForSysMsgOnError)
            else -> chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "Comando '/$command' desconocido. Escribe /help para ver los comandos disponibles.")
        }
        clearNickSuggestions()
    }

    private fun executeMeCommand(args: String?, currentActiveTarget: String?, targetForSysMsgOnError: String) {
        if (args.isNullOrBlank()) {
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "Uso: /me <acción>")
            return
        }
        if (currentActiveTarget.isNullOrBlank() || currentActiveTarget == ChatStateManager.SERVER_TARGET_ID) {
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "El comando /me solo se puede usar en canales o privados.")
            return
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
    }

    private fun executeNickCommand(args: String?, targetForSysMsgOnError: String) {
        if (args.isNullOrBlank()) {
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "Uso: /nick <nuevo_nickname>")
            return
        }
        chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, "Intentando cambiar nick a '$args'...")
        ircRepository.sendRawCommand("NICK $args")
    }

    private fun executeJoinCommand(args: String?, targetForSysMsgOnError: String) {
        val cmdArgs = args?.split(" ", limit = 2)
        val channel = cmdArgs?.getOrNull(0)
        val key = cmdArgs?.getOrNull(1)
        if (channel.isNullOrBlank()) {
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "Uso: /join <#canal> [clave]")
            return
        }
        joinChannel(channel, key) // Reutiliza la función pública joinChannel que ya tiene la lógica de validación y conexión
    }

    private fun executePartCommand(args: String?, currentActiveTarget: String?, targetForSysMsgOnError: String) {
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
        partChannel(channelToPart, partMsg) 
    }

    private fun executeQuitCommand(args: String?) {
        disconnectFromServerAndStopService(args) 
    }

    private fun executeAwayCommand(args: String?, targetForSysMsgOnError: String) {
        if (args.isNullOrBlank()) {
            ircRepository.sendRawCommand("AWAY")
            chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, "Ya no estás marcado como AUSENTE.")
        } else {
            ircRepository.sendRawCommand("AWAY :$args")
            chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, "Ahora estás AUSENTE: $args")
        }
    }

    private fun executeMsgCommand(args: String?, targetForSysMsgOnError: String) {
        val msgParts = args?.split(" ", limit = 2)
        val targetName = msgParts?.getOrNull(0)
        val messageText = msgParts?.getOrNull(1)

        if (targetName.isNullOrBlank() || messageText.isNullOrBlank()) {
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "Uso: /msg <nick/canal> <mensaje>")
            return
        }
        if (targetName.equals(currentNickname, ignoreCase = true)){
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "No puedes enviarte mensajes a ti mismo con /msg.")
            return
        }
        if (targetName.equals(ChatStateManager.SERVER_TARGET_ID, ignoreCase = true)){
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "No puedes enviar mensajes a la pestaña '${ChatStateManager.SERVER_TARGET_ID}' con /msg.")
            return
        }

        val isChannelMsg = targetName.startsWith("#")
        if (!isChannelMsg) {
            if (!chatStateManager.ensurePmTargetExists(targetName, currentNickname)) {
                chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "Nick inválido para /msg: $targetName. No se pudo crear la ventana.")
                return
            }
        }

        val uiMessage = UiChatMessage(
            fullText = "<${currentNickname}> $messageText",
            annotatedString = AnnotatedString("<${currentNickname}> $messageText"),
            type = if (isChannelMsg) UiMessageType.CHANNEL_MSG_SENT else UiMessageType.PRIVATE_MSG_SENT,
            sender = currentNickname,
            isOwnMessage = true
        )
        chatStateManager.addLocalUiMessageToTarget(targetName, uiMessage)
        ircRepository.sendMessage(targetName, messageText)
        chatStateManager.addSystemMessageToTarget(targetName, "Mensaje enviado a $targetName.")
    }

    private fun executeQueryCommand(args: String?, targetForSysMsgOnError: String) {
        val queryParts = args?.split(" ", limit = 2)
        val nick = queryParts?.getOrNull(0)
        val initialMessage = queryParts?.getOrNull(1)

        if (nick.isNullOrBlank()) {
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "Uso: /query <nick> [mensaje opcional]")
            return
        }
        if (nick.equals(currentNickname, ignoreCase = true)){
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "No puedes iniciar /query contigo mismo.")
            return
        }
        if (nick.startsWith("#")){
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "Usa /join para canales, o /msg para enviar mensajes a canales sin unirte.")
            return
        }

        openPrivateMessage(nick) 
        if (!initialMessage.isNullOrBlank()) {
            val uiMessage = UiChatMessage(
                fullText = "<${currentNickname}> $initialMessage",
                annotatedString = AnnotatedString("<${currentNickname}> $initialMessage"),
                type = UiMessageType.PRIVATE_MSG_SENT,
                sender = currentNickname,
                isOwnMessage = true
            )
            chatStateManager.addLocalUiMessageToTarget(nick, uiMessage) 
            ircRepository.sendMessage(nick, initialMessage)
        }
    }

    private fun executeTopicCommand(args: String?, currentActiveTarget: String?, targetForSysMsgOnError: String) {
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
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "Uso: /topic [#canal] [nuevo tema] o úsalo en una ventana de canal.")
            return
        }

        if (targetChannel == null) { // Adicional para seguridad, aunque la lógica anterior debería cubrirlo.
             chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "No se pudo determinar el canal para el comando /topic.")
             return
        }

        if (newTopic != null) {
            ircRepository.sendRawCommand("TOPIC $targetChannel :$newTopic")
            chatStateManager.addSystemMessageToTarget(targetChannel, "Intentando cambiar el tema a: $newTopic")
        } else {
            ircRepository.sendRawCommand("TOPIC $targetChannel")
            chatStateManager.addSystemMessageToTarget(targetChannel, "Solicitando el tema de $targetChannel...")
        }
    }

    private fun executeClearCommand(currentActiveTarget: String?, targetForSysMsgOnError: String) {
        if (!currentActiveTarget.isNullOrBlank()) {
            chatStateManager.clearMessagesForTarget(currentActiveTarget)
            chatStateManager.addSystemMessageToTarget(currentActiveTarget, "Ventana limpiada.")
        } else {
            chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, "No hay ventana activa para limpiar.")
        }
    }

    private fun executeHelpCommand(targetForSysMsgOnError: String) {
        val helpMessage = "Comandos disponibles:\n" +
                        "/me <acción> - Envía una acción.\n" +
                        "/nick <nuevo_nick> - Cambia tu nickname.\n" +
                        "/join <#canal> [clave] - Únete a un canal.\n" +
                        "/part [#canal] [mensaje] - Sal de un canal.\n" +
                        "/msg <nick/canal> <mensaje> - Envía un mensaje privado o a canal.\n" +
                        "/query <nick> [mensaje] - Abre una ventana de chat privado.\n" +
                        "/away [mensaje] - Márcate como ausente.\n" +
                        "/topic [#canal] [nuevo_tema] - Ve o cambia el tema del canal.\n" +
                        "/quit [mensaje] - Desconéctate del servidor.\n" +
                        "/clear - Limpia los mensajes de la ventana actual."
        chatStateManager.addSystemMessageToTarget(targetForSysMsgOnError, helpMessage)
    }
    // --- End Command Handling ---


    @Deprecated("Usar disconnectFromServerAndStopService para una desconexión completa.", ReplaceWith("disconnectFromServerAndStopService(null)"))
    fun disconnect() {
        disconnectFromServerAndStopService(null)
    }

    fun disconnectFromServerAndStopService(quitMessage: String? = null) {
        val targetForMessage = chatStateManager.activeTarget.value ?: chatStateManager.chatTargets.value.firstOrNull() ?: ChatStateManager.SERVER_TARGET_ID
        chatStateManager.addSystemMessageToTarget(targetForMessage, "Desconectando y solicitando detener el servicio...")
        viewModelScope.launch {
            _userMessageEvents.emit("Desconectado del servidor.")
        }
        ircRepository.disconnectAndStopService(quitMessage)
        clearNickSuggestions()
    }

    fun ignoreUser(userName: String) {
        viewModelScope.launch {
            userPreferencesRepository.addIgnoredUser(userName)
            val targetForMessage = chatStateManager.activeTarget.value ?: ChatStateManager.SERVER_TARGET_ID
            chatStateManager.addSystemMessageToTarget(targetForMessage, "Usuario '$userName' ahora está en la lista de ignorados.")
            _snackbarEvents.tryEmit("Usuario '$userName' añadido a ignorados.") 
            firebaseAnalytics.logEvent("ignore_user", null)
        }
    }

    fun performWhois(nick: String) {
        if (nick.isBlank()) {
            Log.w("MainViewModel", "performWhois llamado con nick vacío.")
            return
        }
        val bundle = Bundle()
        bundle.putString("whois_target_nick", nick) 
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

        val textUpToCursor = currentFullText.substring(0, cursorPosition)
        val lastWordStartIndex = textUpToCursor.lastIndexOf(' ') + 1
        val currentWord = textUpToCursor.substring(lastWordStartIndex)

        if (currentWord.length < 2 && !currentWord.startsWith("/")) { 
            _nickSuggestions.value = emptyList()
            return
        }

        val usersInCurrentChannel = currentChannelUserListState.value
        _nickSuggestions.value = usersInCurrentChannel.filter {
            it.startsWith(currentWord, ignoreCase = true) && 
            it.length > currentWord.length 
        }.take(5) 
    }

    fun clearNickSuggestions() {
        _nickSuggestions.value = emptyList()
    }

    fun onNickSuggestionSelected(suggestion: String, currentFullText: String, cursorPosition: Int): String {
        val textUpToCursor = currentFullText.substring(0, cursorPosition)
        // Find the start index of the word being completed.
        // This is the position after the last space before the cursor, or 0 if no space.
        val wordStartIndex = textUpToCursor.lastIndexOf(' ').let { if (it == -1) 0 else it + 1 }

        val prefix = currentFullText.substring(0, wordStartIndex)
        val suffix = currentFullText.substring(cursorPosition)

        clearNickSuggestions()
        // Construct the new text: prefix + suggestion + a single space + the rest of the original text (trimmed of leading spaces).
        return "$prefix$suggestion ${suffix.trimStart()}"
    }
}
