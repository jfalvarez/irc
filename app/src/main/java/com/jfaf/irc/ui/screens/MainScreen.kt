package com.jfaf.irc.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.jfaf.irc.MainActivity
import com.jfaf.irc.R
import com.jfaf.irc.ui.screens.chat.AppDrawerContent
import com.jfaf.irc.ui.screens.chat.ChannelUserListView
import com.jfaf.irc.ui.screens.chat.ChatTopAppBar
import com.jfaf.irc.ui.screens.chat.FullScreenImageViewer
import com.jfaf.irc.ui.screens.chat.MessageInputSection
import com.jfaf.irc.ui.screens.chat.MessagesList
import com.jfaf.irc.ui.screens.connection.ConnectionSetupSection
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.ui.viewmodels.MediaTypeEnum
import com.jfaf.irc.ui.viewmodels.SettingsViewModel
import com.jfaf.irc.ui.viewmodels.SignInViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    signInViewModel: SignInViewModel,
    mainActivity: MainActivity,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState
) {
    val connectionState by viewModel.chatScreenState.connectionState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val selectedMediaForFullScreen by viewModel.selectedMediaForFullScreen.collectAsState()

    LaunchedEffect(connectionState) {
        if (connectionState && drawerState.currentValue == DrawerValue.Open) {
            scope.launch { drawerState.close() }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerMainContent(viewModel = viewModel, connectionState = connectionState, navController = navController) {
                scope.launch { drawerState.close() }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = modifier.imePadding(),
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    if (connectionState) {
                        ChatTopAppBar(
                            activeTarget = viewModel.chatScreenState.activeTarget.collectAsState().value,
                            onNavigationIconClick = { scope.launch { drawerState.open() } },
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
                        activeTargetValue = viewModel.chatScreenState.activeTarget.collectAsState().value,
                        nickSuggestionsState = viewModel.chatScreenState.nickSuggestions.collectAsState().value
                    )
                }
            ) { paddingValues ->
                MainScreenScaffoldContent(
                    paddingValues = paddingValues,
                    connectionState = connectionState,
                    viewModel = viewModel,
                    signInViewModel = signInViewModel,
                    mainActivity = mainActivity,
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
}

@Composable
private fun AppDrawerMainContent(
    viewModel: MainViewModel,
    connectionState: Boolean,
    navController: NavHostController,
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
            }
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
                stringResource(R.string.status_not_connected),
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
    activeTargetValue: String?,
    nickSuggestionsState: List<String>
) {
    Column {
        val showInputSection = connectionState && activeTargetValue != null

        if (connectionState) {
            AndroidView(
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = "ca-app-pub-3940256099942544/6300978111" // TEST BANNER ID
                        Log.d("AdViewConfig", "AdView adUnitId set to: $adUnitId")
                        loadAd(AdRequest.Builder().build())
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

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
                onClearSuggestions = { viewModel.clearNickSuggestions() }
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun MainScreenScaffoldContent(
    paddingValues: PaddingValues,
    connectionState: Boolean,
    viewModel: MainViewModel,
    signInViewModel: SignInViewModel,
    mainActivity: MainActivity,
    onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit
) {
    var nicknameInput by remember { mutableStateOf("") }
    var useSslInput by remember { mutableStateOf(false) }
    val nickServPasswordInputState = remember { mutableStateOf("") }
    val rememberNickServPasswordInputState = remember { mutableStateOf(false) }

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
                useSsl = useSslInput,
                onUseSslChange = { useSslInput = it },
                nickServPasswordState = nickServPasswordInputState,
                rememberNickServPasswordState = rememberNickServPasswordInputState,
                signInViewModel = signInViewModel,
                mainActivity = mainActivity,
                onConnect = { nick, ssl, nickServPass, rememberPass ->
                    if (nick.isNotBlank()) {
                        viewModel.connect(nick, ssl, nickServPass, rememberPass)
                    }
                }
            )
        } else {
            ConnectedStateView(viewModel = viewModel, onMediaClick = onMediaClick)
        }
    }
}

@Composable
private fun ConnectedStateView(
    viewModel: MainViewModel,
    onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit
) {
    val showUserListState by viewModel.chatScreenState.showUserList.collectAsState()
    val messages by viewModel.chatScreenState.uiMessages.collectAsState()
    val activeTarget by viewModel.chatScreenState.activeTarget.collectAsState()
    val showMediaPreviews by viewModel.chatScreenState.showMediaPreviews.collectAsState()

    val currentTargetIsChannel = activeTarget?.startsWith("#") == true
    val shouldShowUserListComposite = showUserListState && currentTargetIsChannel

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(IRCTheme.gradientBrush)
    ) {
        MessagesList(
            messages = messages,
            onMediaClick = onMediaClick,
            modifier = Modifier.weight(if (shouldShowUserListComposite) 0.6f else 1f),
            showMediaPreviews = showMediaPreviews
        )

        AnimatedVisibility(
            visible = shouldShowUserListComposite,
            modifier = Modifier.weight(0.4f)
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
