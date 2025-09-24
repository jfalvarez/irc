package com.jfaf.irc.ui.screens

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView // Import AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.jfaf.irc.R
import com.jfaf.irc.ui.screens.chat.ChannelUserListView
import com.jfaf.irc.ui.screens.chat.ChatTopAppBar
import com.jfaf.irc.ui.screens.chat.FullScreenImageViewer
import com.jfaf.irc.ui.screens.chat.MessagesList
import com.jfaf.irc.ui.screens.chat.MessageInputSection
import com.jfaf.irc.ui.screens.connection.ConnectionSetupSection
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.ui.viewmodels.MainViewModel
import kotlinx.coroutines.launch
import com.jfaf.irc.ui.screens.chat.AppDrawerContent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
    navController: NavHostController,
    snackbarHostState: SnackbarHostState
) {
    val connectionState by viewModel.chatScreenState.connectionState.collectAsState()
    var nicknameInput by remember { mutableStateOf("") }
    var useSslInput by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedImageUrlForFullScreen by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connectionState) {
        if (connectionState && drawerState.currentValue == DrawerValue.Open) {
            scope.launch { drawerState.close() }
        }
    }

    val unreadTargetsState = viewModel.chatScreenState.unreadTargets.collectAsState()
    val nickSuggestionsState by viewModel.chatScreenState.nickSuggestions.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            if (connectionState) {
                AppDrawerContent(
                    chatTargets = viewModel.chatScreenState.chatTargets.collectAsState().value,
                    activeTarget = viewModel.chatScreenState.activeTarget.collectAsState().value,
                    unreadTargets = unreadTargetsState.value,
                    currentNick = viewModel.currentNickname,
                    onTargetSelected = {
                        viewModel.setActiveTarget(it)
                        scope.launch { drawerState.close() }
                    },
                    onJoinChannelRequest = { scope.launch { drawerState.close() } }, 
                    onOpenPrivateMessageRequest = { scope.launch { drawerState.close() } }, 
                    onCloseTargetAction = { viewModel.closeTarget(it) },
                    onSettingsClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("settings")
                    }
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxHeight().padding(16.dp),
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
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
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
                            onToggleUserList = { viewModel.toggleUserListVisibility() }
                        )
                    }
                },
                bottomBar = {
                    Column {
                        val activeTargetValue = viewModel.chatScreenState.activeTarget.collectAsState().value
                        val serverString = stringResource(R.string.cd_server)
                        val showInputSection = connectionState && activeTargetValue != null && activeTargetValue != serverString
                        
                        // Ad Banner - Visible when connected
                        if (connectionState) {
                            AndroidView(
                                factory = { context ->
                                    AdView(context).apply {
                                        setAdSize(AdSize.BANNER)
                                        // Replace with your real Ad Unit ID in production and use test ID for development
                                        adUnitId = "ca-app-pub-3940256099942544/6300978111" // TEST BANNER ID
                                        loadAd(AdRequest.Builder().build())
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (showInputSection) {
                            if (activeTargetValue!!.startsWith("#")) { // Autocomplete only for channels
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
                            } else { // For PMs or if logic changes
                                MessageInputSection(
                                    modifier = Modifier.fillMaxWidth(),
                                    nickSuggestions = emptyList(), // No suggestions for PMs
                                    onSendMessage = { viewModel.sendMessage(it) },
                                    onTextInputChanged = { _, _ -> /* No-op for PMs or non-channels */ },
                                    onSuggestionSelected = { _, _, _ -> "" /* Should not be called */ },
                                    onClearSuggestions = { /* No-op */ }
                                )
                            }
                        }
                    }
                },
                modifier = modifier
            ) { paddingValues ->
                AnimatedContent(
                    targetState = connectionState,
                    modifier = Modifier.fillMaxSize().padding(paddingValues), // This paddingValues from Scaffold adjusts for topBar and bottomBar
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
                            onConnect = {
                                if (nicknameInput.isNotBlank()) {
                                    viewModel.connect(nicknameInput, useSslInput)
                                }
                            }
                            // Removed erroneous modifier from here
                        )
                    } else {
                        val showUserListState by viewModel.chatScreenState.showUserList.collectAsState()
                        val activeTarget by viewModel.chatScreenState.activeTarget.collectAsState()
                        val serverString = stringResource(R.string.cd_server)

                        val currentTargetIsChannel = activeTarget?.startsWith("#") == true && activeTarget != serverString
                        val shouldDisplayUserList = showUserListState && currentTargetIsChannel

                        Row(modifier = Modifier.fillMaxSize().background(IRCTheme.gradientBrush)) {
                            Box(modifier = Modifier.weight(if (shouldDisplayUserList) 0.6f else 1f)) {
                                val messages = viewModel.chatScreenState.uiMessages.collectAsState().value
                                if (activeTarget != null) {
                                    MessagesList(
                                        messages = messages,
                                        onImageClick = { selectedImageUrlForFullScreen = it }
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            stringResource(R.string.prompt_select_channel_or_conversation),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                            if (shouldDisplayUserList) {
                                VerticalDivider(
                                    modifier = Modifier.fillMaxHeight().width(DividerDefaults.Thickness),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                                Box(modifier = Modifier.weight(0.4f)) {
                                    ChannelUserListView(mainViewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = selectedImageUrlForFullScreen != null,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                selectedImageUrlForFullScreen?.let {
                    FullScreenImageViewer(
                        imageUrl = it,
                        onClose = { selectedImageUrlForFullScreen = null }
                    )
                }
            }
        }
    }
}
