package com.jfaf.irc.ui.viewmodels

import android.util.Log
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.data.repositories.UserMetadataRepository
import com.jfaf.irc.domain.usecase.AttemptNickServIdentificationUseCase
import com.jfaf.irc.domain.usecase.CloseTargetUseCase
import com.jfaf.irc.domain.usecase.ConnectUseCase
import com.jfaf.irc.domain.usecase.DisconnectUseCase
import com.jfaf.irc.domain.usecase.HandleIncomingMessageUseCase
import com.jfaf.irc.domain.usecase.IgnoreUserUseCase
import com.jfaf.irc.domain.usecase.JoinChannelUseCase
import com.jfaf.irc.domain.usecase.OpenPrivateMessageUseCase
import com.jfaf.irc.domain.usecase.PartChannelUseCase
import com.jfaf.irc.domain.usecase.PerformWhoisUseCase
import com.jfaf.irc.domain.usecase.SendMessageActionStatus
import com.jfaf.irc.domain.usecase.SendMessageOrCommandUseCase
import com.jfaf.irc.domain.usecase.WhoisRequestResult
import com.jfaf.irc.ui.viewmodels.command.CommandResult
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MediaTypeEnum {
    IMAGE,
    VIDEO,
    NONE
}

data class UiChatMessage(
    val fullText: String,
    val annotatedString: AnnotatedString? = null,
    val type: UiMessageType,
    val sender: String? = null,
    val isOwnMessage: Boolean = false,
    val mediaUrl: String? = null, // Replaced imageUrl
    val mediaType: MediaTypeEnum = MediaTypeEnum.NONE // Added mediaType
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
    val nickSuggestions: StateFlow<List<String>>,
    val showMediaPreviews: StateFlow<Boolean>,
    val onlineFriends: StateFlow<Set<String>>
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val ircRepository: IrcRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userMetadataRepository: UserMetadataRepository,
    private val chatStateManager: ChatStateManager,
    private val connectUseCase: ConnectUseCase,
    private val attemptNickServIdentificationUseCase: AttemptNickServIdentificationUseCase,
    private val disconnectUseCase: DisconnectUseCase,
    private val ignoreUserUseCase: IgnoreUserUseCase,
    private val performWhoisUseCase: PerformWhoisUseCase,
    private val joinChannelUseCase: JoinChannelUseCase,
    private val openPrivateMessageUseCase: OpenPrivateMessageUseCase,
    private val partChannelUseCase: PartChannelUseCase,
    private val closeTargetUseCase: CloseTargetUseCase,
    private val sendMessageOrCommandUseCase: SendMessageOrCommandUseCase,
    private val handleIncomingMessageUseCase: HandleIncomingMessageUseCase
) : ViewModel(), ChatEventListener {

    private var chatEventOrchestrator = ChatEventOrchestrator(
        ircRepository,
        userMetadataRepository,
        this,
        viewModelScope
    )

    private data class UiMessagesFilterContext(
        val activeTarget: String?,
        val allMessages: Map<String, List<UiChatMessage>>,
        val showJpq: Boolean,
        val showNick: Boolean,
        val showMode: Boolean,
        val ignoredUsersLowercase: Set<String>
    )

    var currentNickname = "IrcUser${(100..999).random()}"
        private set
    private var sessionNickServPasswordForAutoIdentify: String? = null

    private val _incomingPrivateMessageEvent = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val incomingPrivateMessageEvent: SharedFlow<String> = _incomingPrivateMessageEvent.asSharedFlow()

    private val _userMessageEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val userMessageEvents: SharedFlow<String> = _userMessageEvents.asSharedFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _nickSuggestions = MutableStateFlow<List<String>>(emptyList())

    // State for FullScreenImageViewer
    private val _selectedMediaForFullScreen = MutableStateFlow<Pair<String, MediaTypeEnum>?>(null)
    val selectedMediaForFullScreen: StateFlow<Pair<String, MediaTypeEnum>?> = _selectedMediaForFullScreen.asStateFlow()

    private val showJoinPartQuitMessagesPref: StateFlow<Boolean> =
        userPreferencesRepository.showJoinPartQuitFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    private val showNickChangesPref: StateFlow<Boolean> =
        userPreferencesRepository.showNickChangesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    private val showModeChangesPref: StateFlow<Boolean> =
        userPreferencesRepository.showModeChangesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val ignoredUsersPref: StateFlow<Set<String>> =
        userMetadataRepository.ignoredUsersFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val showMediaPreviewsPref: StateFlow<Boolean> =
        userPreferencesRepository.showMediaPreviewsFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            true
        )

    private val onlineFriendsState: StateFlow<Set<String>> = userMetadataRepository.friendsFlow
        .combine(chatStateManager.usersInChannel) { friends, usersInChannel ->
            val allOnlineUsers = usersInChannel.values.flatten().toSet()
            friends.filter { friend -> allOnlineUsers.any { onlineUser -> onlineUser.equals(friend, ignoreCase = true) } }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private fun shouldDisplayMessage(message: UiChatMessage, filterContext: UiMessagesFilterContext): Boolean {
        if (message.sender != null &&
            (message.type == UiMessageType.CHANNEL_MSG_RECEIVED ||
             message.type == UiMessageType.PRIVATE_MSG_RECEIVED ||
             message.type == UiMessageType.ACTION_MSG) &&
            message.sender.lowercase() in filterContext.ignoredUsersLowercase) {
            return false
        }

        if (!filterContext.showJpq &&
            (message.type == UiMessageType.JOIN_PART_QUIT ||
             message.fullText.contains("signed off", ignoreCase = true) ||
             (message.annotatedString?.text?.contains("signed off", ignoreCase = true) == true) ||
             message.fullText.contains("connection closed", ignoreCase = true) ||
             (message.annotatedString?.text?.contains("connection closed", ignoreCase = true) == true))) {
            return false
        }

        if (!filterContext.showNick && message.type == UiMessageType.NICK_CHANGE) {
            return false
        }

        if (!filterContext.showMode && message.type == UiMessageType.MODE_CHANGE) {
            return false
        }

        return true
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
        nickSuggestions = _nickSuggestions.asStateFlow(),
        showMediaPreviews = showMediaPreviewsPref,
        onlineFriends = onlineFriendsState
    )

    init {
        chatEventOrchestrator.startObservingRawMessages()
        ircRepository.connectionState.onEach { isConnected ->
            Log.i("MainViewModel", "Connection State Observer: ${if (isConnected) "CONNECTED" else "DISCONNECTED"}")
            if (!isConnected) {
                handleServiceDisconnected()
            } else {
                handleServiceConnected()
                attemptNickServIdentification()
            }
        }.launchIn(viewModelScope)
    }

    // --- Functions for FullScreenImageViewer State ---
    fun userClickedOnMedia(mediaUrl: String, mediaType: MediaTypeEnum) {
        _selectedMediaForFullScreen.value = Pair(mediaUrl, mediaType)
    }

    fun clearExpandedMedia() {
        _selectedMediaForFullScreen.value = null
    }
    // --- End Functions for FullScreenImageViewer State ---

    private fun attemptNickServIdentification() {
        viewModelScope.launch {
            attemptNickServIdentificationUseCase(this@MainViewModel.currentNickname, sessionNickServPasswordForAutoIdentify)
            sessionNickServPasswordForAutoIdentify = null
        }
    }

    override fun processMessageForUi(parsedMessage: ParsedIrcMessage) {
        viewModelScope.launch {
            val result = handleIncomingMessageUseCase(parsedMessage, this@MainViewModel.currentNickname)

            if (result.messageProcessed) {
                result.newCurrentNickname?.let {
                    Log.d("MainViewModel", "Own nick change to '${it}' (via HandleIncomingMessageUseCase) confirmed.")
                    this@MainViewModel.currentNickname = it
                }
                result.privateMessageEventNick?.let {
                    emitPrivateMessageEvent(it)
                }
            }
        }
    }

    override fun emitPrivateMessageEvent(nick: String) {
        Log.d("MainViewModel", "PM Event for '$nick' being emitted via _incomingPrivateMessageEvent.")
        _incomingPrivateMessageEvent.tryEmit(nick)
    }

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
            chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, messageText, isError = true)
        }

        chatStateManager.resetStateForDisconnection()
    }

    fun connect(nickname: String, ssl: Boolean, sessionNickServPasswordParam: String?, rememberPass: Boolean) {
        this.currentNickname = nickname
        this.sessionNickServPasswordForAutoIdentify = sessionNickServPasswordParam

        viewModelScope.launch {
            connectUseCase(nickname, ssl, sessionNickServPasswordParam, rememberPass)
        }
    }

    fun joinChannel(channelName: String, key: String? = null) {
        viewModelScope.launch {
            joinChannelUseCase(channelName, key)
        }
    }

    fun openPrivateMessage(nick: String, initialMessage: String? = null) {
        viewModelScope.launch {
            openPrivateMessageUseCase(nick, this@MainViewModel.currentNickname, initialMessage)
        }
    }

    fun setActiveTarget(targetName: String) {
        chatStateManager.setActiveTarget(targetName, this.currentNickname)
        clearNickSuggestions()
    }

    fun partChannel(channelName: String?, partMessage: String? = null) {
        viewModelScope.launch {
            partChannelUseCase(channelName, chatStateManager.activeTarget.value, partMessage)
        }
    }

    fun closeTarget(targetName: String) {
        val oldActiveTarget = chatStateManager.activeTarget.value
        viewModelScope.launch {
            closeTargetUseCase(targetName, oldActiveTarget, this@MainViewModel.currentNickname)
        }
        if (oldActiveTarget == targetName) {
            clearNickSuggestions()
        }
    }

    fun sendMessage(messageContent: String) {
        viewModelScope.launch {
            val status = sendMessageOrCommandUseCase(
                messageContent = messageContent,
                currentActiveTargetFromViewModel = chatStateManager.activeTarget.value,
                currentOwnNickname = this@MainViewModel.currentNickname
            )

            when (status) {
                is SendMessageActionStatus.ViewModelActionNeeded -> {
                    when (val commandResult = status.commandResult) {
                        is CommandResult.JoinChannel -> joinChannel(commandResult.channel, commandResult.key)
                        is CommandResult.PartChannel -> {
                            val channelToPart = commandResult.channelToPart ?: chatStateManager.activeTarget.value
                            partChannel(channelToPart, commandResult.partMessage)
                        }
                        is CommandResult.OpenQuery -> {
                            openPrivateMessage(commandResult.nick, commandResult.initialMessage)
                        }
                        is CommandResult.Quit -> disconnectFromServerAndStopService(commandResult.quitMessage)
                        else -> {
                            Log.w("MainViewModel", "Unhandled CommandResult in ViewModelActionNeeded: $commandResult")
                        }
                    }
                }
                SendMessageActionStatus.Success -> { /* Handled by UseCase */ }
                SendMessageActionStatus.NotConnected -> { /* Handled by UseCase */ }
                SendMessageActionStatus.BlankInput -> { /* Handled by UseCase */ }
                SendMessageActionStatus.CannotSendToTarget -> { /* Handled by UseCase */ }
            }
            clearNickSuggestions()
        }
    }

    @Deprecated("Usar disconnectFromServerAndStopService para una desconexión completa.", ReplaceWith("disconnectFromServerAndStopService(null)"))
    fun disconnect() {
        disconnectFromServerAndStopService(null)
    }

    fun disconnectFromServerAndStopService(quitMessage: String? = null) {
        viewModelScope.launch {
            disconnectUseCase(quitMessage)
            _userMessageEvents.emit("Desconectado del servidor.")
        }
        clearNickSuggestions()
    }

    fun ignoreUser(userName: String) {
        viewModelScope.launch {
            ignoreUserUseCase(userName)
            _snackbarEvents.tryEmit("Usuario '$userName' añadido a ignorados.")

            if (chatStateManager.activeTarget.value?.equals(userName, ignoreCase = true) == true) {
                closeTarget(userName)
            }
        }
    }

    fun performWhois(nick: String) {
        if (nick.isBlank()) {
            Log.w("MainViewModel", "performWhois llamado con nick vacío.")
            return
        }
        viewModelScope.launch {
            when (performWhoisUseCase(nick)) {
                is WhoisRequestResult.Success -> {
                    _snackbarEvents.tryEmit("WHOIS para '$nick' solicitado. Ver pestaña 'Servidor'.")
                }
                is WhoisRequestResult.NotConnected -> {
                    _snackbarEvents.tryEmit("Error: No conectado al servidor para enviar WHOIS.")
                }
                is WhoisRequestResult.InvalidNickInput -> {
                    Log.w("MainViewModel", "performWhoisUseCase reported InvalidNickInput for: $nick")
                }
            }
        }
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
        val wordStartIndex = textUpToCursor.lastIndexOf(' ').let { if (it == -1) 0 else it + 1 }

        val prefix = currentFullText.substring(0, wordStartIndex)
        val suffix = currentFullText.substring(cursorPosition)

        clearNickSuggestions()
        return "$prefix$suggestion ${suffix.trimStart()}"
    }
}
