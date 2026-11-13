package com.jfaf.irc.ui.screens

import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigation.NavHostController
import com.jfaf.irc.ui.screens.chat.AppDrawerContent
import com.jfaf.irc.ui.screens.chat.ChannelUserListView
import com.jfaf.irc.ui.screens.chat.ChatTopAppBar
import com.jfaf.irc.ui.screens.chat.FullScreenImageViewer
import com.jfaf.irc.ui.screens.chat.MessageInputSection
import com.jfaf.irc.ui.screens.chat.MessagesList
import com.jfaf.irc.ui.screens.connection.ConnectionSetupSection
import com.jfaf.irc.domain.chat.ChatStateManager
import com.jfaf.irc.domain.chat.TargetNavigation
import com.jfaf.irc.ui.platform.LocalPlatformUi
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.domain.chat.model.MediaTypeEnum
import com.jfaf.irc.ui.viewmodels.SettingsViewModel
import com.jfaf.irc.ui.viewmodels.SignInViewModel
import kotlinx.coroutines.launch

/** Ancho a partir del cual la versión de escritorio usa paneles fijos (el "expandido" de Material). */
private val WIDE_LAYOUT_MIN_WIDTH = 840.dp
private val USER_LIST_FIXED_WIDTH = 240.dp

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    signInViewModel: SignInViewModel,
    onGoogleSignIn: () -> Unit,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState
) {
    val connectionState by viewModel.chatScreenState.connectionState.collectAsState()
    val isReconnecting by viewModel.chatScreenState.isReconnecting.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val selectedMediaForFullScreen by viewModel.selectedMediaForFullScreen.collectAsState()

    LaunchedEffect(connectionState) {
        if (connectionState) {
            viewModel.loadNativeAd()
            if (drawerState.currentValue == DrawerValue.Open) {
                scope.launch { drawerState.close() }
            }
        }
    }

    val platformUi = LocalPlatformUi.current
    val chatTargets by viewModel.chatScreenState.chatTargets.collectAsState()
    val activeTarget by viewModel.chatScreenState.activeTarget.collectAsState()

    // Atajos de teclado: Ctrl+Tab / Ctrl+Mayús+Tab cambian de pestaña y Ctrl+W cierra la activa (menos Servidor).
    val shortcuts = Modifier.chatShortcuts(
        onNextTab = { TargetNavigation.neighbour(chatTargets, activeTarget, forward = true)?.let(viewModel::setActiveTarget) },
        onPreviousTab = { TargetNavigation.neighbour(chatTargets, activeTarget, forward = false)?.let(viewModel::setActiveTarget) },
        onCloseTab = { activeTarget?.takeIf { it != ChatStateManager.SERVER_TARGET_ID }?.let(viewModel::closeTarget) }
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    // Ventana ancha de escritorio: pestañas fijas a la izquierda y usuarios a la derecha, sin cajón.
    val wide = platformUi.isDesktop && connectionState && maxWidth >= WIDE_LAYOUT_MIN_WIDTH

    val screenContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize().background(IRCTheme.gradientBrush)) {
            Scaffold(
                modifier = modifier.imePadding(),
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    if (connectionState) {
                        ChatTopAppBar(
                            activeTarget = viewModel.chatScreenState.activeTarget.collectAsState().value,
                            onNavigationIconClick = { scope.launch { drawerState.open() } },
                            showNavigationIcon = !wide,
                            showUserListOption = !wide,
                            onDisconnectClick = { viewModel.disconnectFromServerAndStopService() },
                            onJoinChannelRequest = { viewModel.joinChannel(it) },
                            onOpenPrivateMessageRequest = { viewModel.openPrivateMessage(it) },
                            onToggleUserList = { viewModel.toggleUserListVisibility() },
                            onIgnoreUserInPm = { nick -> viewModel.ignoreUser(nick) },
                            onAddFriendInPm = { nick -> settingsViewModel.addFriend(nick) },
                            onWhoisClick = { nick -> viewModel.performWhois(nick) }
                        )
                    }
                },
                bottomBar = {
                    MainScreenBottomBar(
                        viewModel = viewModel,
                        connectionState = connectionState,
                        isReconnecting = isReconnecting,
                        activeTargetValue = viewModel.chatScreenState.activeTarget.collectAsState().value,
                        nickSuggestionsState = viewModel.chatScreenState.nickSuggestions.collectAsState().value,
                        autoFocusInput = platformUi.isDesktop
                    )
                }
            ) { paddingValues ->
                MainScreenScaffoldContent(
                    paddingValues = paddingValues,
                    connectionState = connectionState,
                    viewModel = viewModel,
                    signInViewModel = signInViewModel,
                    onGoogleSignIn = onGoogleSignIn,
                    fixedUserList = wide,
                    onMediaClick = { mediaUrl, mediaType ->
                        viewModel.userClickedOnMedia(mediaUrl, mediaType)
                    }
                )
            }
            AnimatedVisibility(
                visible = selectedMediaForFullScreen != null,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                selectedMediaForFullScreen?.let { (mediaUrl, mediaType) ->
                    FullScreenImageViewer(
                        mediaUrl = mediaUrl,
                        mediaType = mediaType,
                        onClose = { viewModel.clearExpandedMedia() }
                    )
                }
            }
        }
    }

    val openDrawerContent: @Composable (closeAction: () -> Unit) -> Unit = { closeAction ->
        AppDrawerMainContent(
            viewModel = viewModel,
            connectionState = connectionState,
            navController = navController,
            permanent = wide,
            closeDrawerAction = closeAction
        )
    }

    if (wide) {
        Row(modifier = Modifier.fillMaxSize().then(shortcuts)) {
            openDrawerContent {}
            Box(modifier = Modifier.weight(1f)) { screenContent() }
        }
    } else {
        ModalNavigationDrawer(
            modifier = shortcuts,
            drawerState = drawerState,
            drawerContent = { openDrawerContent { scope.launch { drawerState.close() } } }
        ) {
            screenContent()
        }
    }
    } // BoxWithConstraints
}

@Composable
private fun AppDrawerMainContent(
    viewModel: MainViewModel,
    connectionState: Boolean,
    navController: NavHostController,
    permanent: Boolean = false,
    closeDrawerAction: () -> Unit
) {
    val onlineFriends by viewModel.chatScreenState.onlineFriends.collectAsState()

    if (connectionState) {
        AppDrawerContent(
            chatTargets = viewModel.chatScreenState.chatTargets.collectAsState().value,
            activeTarget = viewModel.chatScreenState.activeTarget.collectAsState().value,
            unreadTargets = viewModel.chatScreenState.unreadTargets.collectAsState().value,
            onlineFriends = onlineFriends,
            currentNick = viewModel.currentNickname,
            onTargetSelected = {
                viewModel.setActiveTarget(it)
                closeDrawerAction()
            },
            onCloseTargetAction = { viewModel.closeTarget(it) },
            onOpenPrivateMessage = {
                viewModel.openPrivateMessage(it)
                closeDrawerAction()
            },
            onSettingsClick = {
                closeDrawerAction()
                navController.navigate("settings")
            },
            permanent = permanent
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(Res.string.status_not_connected),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MainScreenBottomBar(
    viewModel: MainViewModel,
    connectionState: Boolean,
    isReconnecting: Boolean,
    activeTargetValue: String?,
    nickSuggestionsState: List<String>,
    autoFocusInput: Boolean
) {
    Column {
        val showInputSection = connectionState && !isReconnecting && activeTargetValue != null

        if (showInputSection) {
            MessageInputSection(
                modifier = Modifier.fillMaxWidth(),
                nickSuggestions = nickSuggestionsState,
                onSendMessage = { viewModel.sendMessage(it) },
                onTextInputChanged = { text, cursorPos ->
                    viewModel.updateNickSuggestions(text, cursorPos)
                },
                onSuggestionSelected = { suggestion, currentText, cursorPos ->
                    viewModel.onNickSuggestionSelected(suggestion, currentText, cursorPos)
                },
                onClearSuggestions = { viewModel.clearNickSuggestions() },
                autoFocus = autoFocusInput,
                focusKey = activeTargetValue
            )
        }
    }
}

@Composable
private fun MainScreenScaffoldContent(
    paddingValues: PaddingValues,
    connectionState: Boolean,
    viewModel: MainViewModel,
    signInViewModel: SignInViewModel,
    onGoogleSignIn: () -> Unit,
    fixedUserList: Boolean,
    onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit
) {
    val userPreferences by viewModel.userPreferences.collectAsState()

    var nicknameInput by remember { mutableStateOf(userPreferences.nickname) }
    val nickServPasswordInputState = remember { mutableStateOf(userPreferences.nickServPassword) }
    val rememberNickServPasswordInputState = remember { mutableStateOf(userPreferences.nickServPassword.isNotEmpty()) }

    LaunchedEffect(userPreferences) {
        if (nicknameInput != userPreferences.nickname) {
            nicknameInput = userPreferences.nickname
        }
        if (nickServPasswordInputState.value != userPreferences.nickServPassword) {
            nickServPasswordInputState.value = userPreferences.nickServPassword
        }
        val shouldRemember = userPreferences.nickServPassword.isNotEmpty()
        if (rememberNickServPasswordInputState.value != shouldRemember) {
            rememberNickServPasswordInputState.value = shouldRemember
        }
    }

    AnimatedContent(
        targetState = connectionState,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        transitionSpec = {
            if (targetState) {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            } else {
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            }
        },
        label = "ConnectionStateAnimation"
    ) { targetIsConnected ->
        if (!targetIsConnected) {
            ConnectionSetupSection(
                nickname = nicknameInput,
                onNicknameChange = { nicknameInput = it },
                nickServPasswordState = nickServPasswordInputState,
                rememberNickServPasswordState = rememberNickServPasswordInputState,
                signInViewModel = signInViewModel,
                onGoogleSignIn = onGoogleSignIn,
                onConnect = { nick, nickServPass, rememberPass ->
                    if (nick.isNotBlank()) {
                        viewModel.connect(nick, true, nickServPass, rememberPass)
                    }
                }
            )
        } else {
            ConnectedStateView(
                viewModel = viewModel,
                fixedUserList = fixedUserList,
                onMediaClick = onMediaClick
            )
        }
    }
}

@Composable
private fun ConnectedStateView(
    viewModel: MainViewModel,
    fixedUserList: Boolean,
    onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit
) {
    val currentAd by viewModel.currentAd.collectAsState()
    val showUserListState by viewModel.chatScreenState.showUserList.collectAsState()
    val messages by viewModel.chatScreenState.uiMessages.collectAsState()
    val activeTarget by viewModel.chatScreenState.activeTarget.collectAsState()
    val showMediaPreviews by viewModel.chatScreenState.showMediaPreviews.collectAsState()
    val isReconnecting by viewModel.chatScreenState.isReconnecting.collectAsState()

    val currentTargetIsChannel = activeTarget?.startsWith("#") == true
    // En la ventana ancha los usuarios van en un panel fijo de la derecha, sin depender del botón.
    val shouldShowUserListComposite = (fixedUserList || showUserListState) && currentTargetIsChannel

    Column(modifier = Modifier.fillMaxSize()) {
    if (isReconnecting) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.status_reconnecting),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        MessagesList(
            messages = messages,
            onMediaClick = onMediaClick,
            modifier = Modifier.weight(if (shouldShowUserListComposite && !fixedUserList) 0.6f else 1f),
            showMediaPreviews = showMediaPreviews,
            ad = currentAd,
            mainViewModel = viewModel
        )

        AnimatedVisibility(
            visible = shouldShowUserListComposite,
            modifier = if (fixedUserList) Modifier.width(USER_LIST_FIXED_WIDTH) else Modifier.weight(0.4f)
        ) {
            Row {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = 1.dp,
                    color = DividerDefaults.color
                )
                ChannelUserListView(
                    mainViewModel = viewModel
                )
            }
        }
    }
    }
}
