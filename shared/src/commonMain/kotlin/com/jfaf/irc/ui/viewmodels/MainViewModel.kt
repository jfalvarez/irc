package com.jfaf.irc.ui.viewmodels

import com.jfaf.irc.domain.chat.ChatEventListener
import com.jfaf.irc.domain.chat.ChatEventOrchestrator
import com.jfaf.irc.domain.chat.ChatHistoryPersister
import com.jfaf.irc.domain.chat.ChatStateManager
import com.jfaf.irc.domain.chat.LinkState
import com.jfaf.irc.domain.chat.LinkTransitions
import com.jfaf.irc.domain.chat.MessageFilterOptions
import com.jfaf.irc.domain.chat.NickAutocomplete
import com.jfaf.irc.domain.chat.UiMessageFilter
import com.jfaf.irc.domain.chat.model.MediaTypeEnum
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import com.jfaf.irc.log.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jfaf.irc.domain.platform.AdHandle
import com.jfaf.irc.domain.platform.AdProvider
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.data.prefs.UserPreferences
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.domain.IrcConnection
import com.jfaf.irc.data.repositories.SilentWhoisCompletionSignal
import com.jfaf.irc.domain.user.UserMetadataStore
import com.jfaf.irc.domain.user.FriendPresenceTracker
import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
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
import com.jfaf.irc.domain.usecase.UpdateFriendMonitoringUseCase
import com.jfaf.irc.domain.usecase.WhoisRequestResult
import com.jfaf.irc.domain.chat.command.CommandResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

data class ChatScreenState(
    val activeTarget: StateFlow<String?>,
    val chatTargets: StateFlow<List<String>>,
    val unreadTargets: StateFlow<Set<String>>,
    /** Privados sin leer por conversación (para la insignia del cajón y el punto del menú). */
    val unreadPmCounts: StateFlow<Map<String, Int>>,
    val uiMessages: StateFlow<List<UiChatMessage>>,
    /** true si está conectado O reconectando (la UI mantiene el chat visible mientras reconecta). */
    val connectionState: StateFlow<Boolean>,
    val isReconnecting: StateFlow<Boolean>,
    val usersInChannel: StateFlow<Map<String, List<String>>>,
    val currentChannelUserList: StateFlow<List<String>>,
    val showUserList: StateFlow<Boolean>,
    val nickSuggestions: StateFlow<List<String>>,
    val showMediaPreviews: StateFlow<Boolean>,
    val onlineFriends: StateFlow<Set<String>>,
    val ignoredUsers: StateFlow<Set<String>>
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class MainViewModel(
    private val ircConnection: IrcConnection,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userMetadataStore: UserMetadataStore,
    private val friendPresence: FriendPresenceTracker,
    private val chatStateManager: ChatStateManager,
    private val chatHistoryPersister: ChatHistoryPersister,
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
    private val handleIncomingMessageUseCase: HandleIncomingMessageUseCase,
    private val silentWhoisCompletionSignal: SilentWhoisCompletionSignal,
    private val strings: StringProvider,
    private val adProvider: AdProvider
) : ViewModel(), ChatEventListener {

    private var chatEventOrchestrator = ChatEventOrchestrator(
        ircConnection,
        userMetadataStore,
        this,
        viewModelScope,
        strings
    )

    var currentNickname: String = ""
        private set
    private var sessionNickServPasswordForAutoIdentify: String? = null

    val userPreferences: StateFlow<UserPreferences> = 
        userPreferencesRepository.userPreferencesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserPreferences("", "")
        )

    private val _isRegistered = MutableStateFlow(false)
    private var friendCheckJob: Job? = null
    @Volatile private var isFriendCheckPaused = false

    private val _friendCameOnlineEvent = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val friendCameOnlineEvent = _friendCameOnlineEvent.asSharedFlow()

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
        userMetadataStore.ignoredUsersFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val showMediaPreviewsPref: StateFlow<Boolean> =
        userPreferencesRepository.showMediaPreviewsFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            true
        )

    private val onlineFriendsState: StateFlow<Set<String>> = friendPresence.onlineFriendsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Anuncio actual, para que la UI de la plataforma lo pinte. */
    val currentAd: StateFlow<AdHandle?> = adProvider.currentAd

    fun loadNativeAd() {
        adProvider.load()
    }

    @Suppress("UNCHECKED_CAST")
    private val combinedUiMessagesFlow: StateFlow<List<UiChatMessage>> = combine(
        listOf(
            chatStateManager.activeTarget,
            chatStateManager.allMessages,
            showJoinPartQuitMessagesPref,
            showNickChangesPref,
            showModeChangesPref,
            ignoredUsersPref,
            adProvider.currentAd
        )
    ) { values ->
        val activeTarget = values[0] as String?
        val allMessages = values[1] as Map<String, List<UiChatMessage>>
        val options = MessageFilterOptions(
            showJoinPartQuit = values[2] as Boolean,
            showNickChanges = values[3] as Boolean,
            showModeChanges = values[4] as Boolean,
            ignoredUsers = values[5] as Set<String>
        )
        UiMessageFilter.apply(
            messages = allMessages[activeTarget] ?: emptyList(),
            hasAd = values[6] != null,
            options = options
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val currentChannelUserListState: StateFlow<List<String>> =
        chatStateManager.currentChannelUserListFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    /** Último estado de enlace procesado (null hasta el primero); sustituye al antiguo `wasReconnecting`. */
    private var previousLink: LinkState? = null

    private val uiConnectionState: StateFlow<Boolean> =
        combine(ircConnection.connectionState, ircConnection.isReconnecting) { connected, reconnecting ->
            connected || reconnecting
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ircConnection.connectionState.value || ircConnection.isReconnecting.value
        )

    val chatScreenState: ChatScreenState = ChatScreenState(
        activeTarget = chatStateManager.activeTarget,
        chatTargets = chatStateManager.chatTargets,
        unreadTargets = chatStateManager.unreadTargets,
        unreadPmCounts = chatStateManager.unreadPmCounts,
        uiMessages = combinedUiMessagesFlow,
        connectionState = uiConnectionState,
        isReconnecting = ircConnection.isReconnecting,
        usersInChannel = chatStateManager.usersInChannel,
        currentChannelUserList = currentChannelUserListState,
        showUserList = chatStateManager.showUserList,
        nickSuggestions = _nickSuggestions.asStateFlow(),
        showMediaPreviews = showMediaPreviewsPref,
        onlineFriends = onlineFriendsState,
        ignoredUsers = ignoredUsersPref
    )

    init {
        chatEventOrchestrator.startObservingRawMessages()
        chatHistoryPersister.start(viewModelScope)
        // El debounce evita ver el "desconectado" transitorio que ocurre justo antes de que
        // el servicio marque isReconnecting = true.
        combine(ircConnection.connectionState, ircConnection.isReconnecting, LinkState::of)
            .distinctUntilChanged()
            .debounce(300)
            .onEach { state ->
                Log.i("MainViewModel", "Connection State Observer: $state")
                val previous = previousLink
                previousLink = state
                onLinkStateChanged(previous, state)
            }.launchIn(viewModelScope)

        _isRegistered.onEach { isRegistered ->
            if (isRegistered) {
                startFriendChecker()
            } else {
                friendCheckJob?.cancel()
            }
        }.launchIn(viewModelScope)

        friendPresence.friendCameOnlineEvent.onEach { nick ->
            _friendCameOnlineEvent.emit(nick)
            _snackbarEvents.emit(strings.get(Msg.SNACK_FRIEND_ONLINE, nick))
        }.launchIn(viewModelScope)
    }

    private fun startFriendChecker() {
        friendCheckJob?.cancel()
        friendCheckJob = viewModelScope.launch {
            while (true) {
                if (!isFriendCheckPaused) {
                    val friends = userMetadataStore.friendsFlow.first()
                    if (friends.isNotEmpty()) {
                        for (friend in friends) {
                            if (!isFriendCheckPaused) {
                                ircConnection.sendRawCommand("WHOIS $friend", isSilent = true)
                                silentWhoisCompletionSignal.awaitCompletion()
                            }
                        }
                    }
                }
                delay(60000) // Check every 60 seconds
            }
        }
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
            // La contraseña de sesión se conserva en memoria para re-identificar tras una reconexión.
            attemptNickServIdentificationUseCase(this@MainViewModel.currentNickname, sessionNickServPasswordForAutoIdentify)
        }
    }

    override fun processMessageForUi(parsedMessage: ParsedIrcMessage) {
        if (parsedMessage.command == "001") {
            Log.d("MainViewModel", "Registration complete (RPL_WELCOME received).")
            _isRegistered.value = true
        }

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

    private fun onLinkStateChanged(previous: LinkState?, state: LinkState) {
        if (LinkTransitions.isFreshConnection(previous, state)) {
            chatEventOrchestrator.resetSessionState()
            chatStateManager.resetStateForConnection()
        }
        if (state != LinkState.CONNECTED) {
            _isRegistered.value = false
            friendCheckJob?.cancel()
        }

        LinkTransitions.noticeFor(previous, state)?.let {
            chatStateManager.addSystemMessageToTarget(ChatStateManager.SERVER_TARGET_ID, strings.get(it.msg), it.isError)
        }

        when (state) {
            LinkState.CONNECTED -> attemptNickServIdentification()
            LinkState.DISCONNECTED -> chatStateManager.resetStateForDisconnection()
            LinkState.RECONNECTING -> Unit
        }
    }

    fun connect(nickname: String, ssl: Boolean, sessionNickServPasswordParam: String?, rememberPass: Boolean) {
        this.currentNickname = nickname
        this.sessionNickServPasswordForAutoIdentify = sessionNickServPasswordParam

        viewModelScope.launch {
            userPreferencesRepository.updateNickname(nickname)
            if (rememberPass && sessionNickServPasswordParam != null) {
                userPreferencesRepository.updateNickServPassword(sessionNickServPasswordParam)
            } else if (!rememberPass) {
                userPreferencesRepository.clearNickServPassword()
            }
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
            _userMessageEvents.emit(strings.get(Msg.SNACK_DISCONNECTED))
        }
        clearNickSuggestions()
    }

    fun ignoreUser(userName: String) {
        viewModelScope.launch {
            ignoreUserUseCase(userName)
            _snackbarEvents.tryEmit(strings.get(Msg.SNACK_IGNORED_ADDED, userName))

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
            isFriendCheckPaused = true
            when (performWhoisUseCase(nick)) {
                is WhoisRequestResult.Success -> {
                    _snackbarEvents.tryEmit(strings.get(Msg.SNACK_WHOIS_REQUESTED, nick))
                }
                is WhoisRequestResult.NotConnected -> {
                    _snackbarEvents.tryEmit(strings.get(Msg.SNACK_WHOIS_NOT_CONNECTED))
                }
                is WhoisRequestResult.InvalidNickInput -> {
                    Log.w("MainViewModel", "performWhoisUseCase reported InvalidNickInput for: $nick")
                }
            }
            delay(5000) // 5 seconds
            isFriendCheckPaused = false
        }
    }

    // --- Nick Autocompletion Functions ---
    fun updateNickSuggestions(currentFullText: String, cursorPosition: Int) {
        val activeTarget = chatStateManager.activeTarget.value
        _nickSuggestions.value = NickAutocomplete.suggestions(
            activeTarget = activeTarget,
            // Se lee del estado de origen: `currentChannelUserListState` sólo se actualiza mientras alguien lo
            // observa (el panel de usuarios), y con el panel cerrado seguía vacío y no se sugería nada.
            usersInChannel = chatStateManager.usersInChannel.value[activeTarget].orEmpty(),
            text = currentFullText,
            cursorPosition = cursorPosition,
            ownNick = currentNickname
        )
    }

    fun clearNickSuggestions() {
        _nickSuggestions.value = emptyList()
    }

    fun onNickSuggestionSelected(suggestion: String, currentFullText: String, cursorPosition: Int): String {
        clearNickSuggestions()
        return NickAutocomplete.apply(suggestion, currentFullText, cursorPosition)
    }
}
