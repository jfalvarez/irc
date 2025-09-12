package com.jfaf.irc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings // Added import for Settings icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController // NAVEGACIÓN: Nueva importación
import com.jfaf.irc.R
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.ui.viewmodels.UiChatMessage
import com.jfaf.irc.ui.viewmodels.UiMessageType
import android.util.Log
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
    navController: NavHostController // NAVEGACIÓN: Nuevo parámetro
) {
    val connectionState by viewModel.chatScreenState.connectionState.collectAsState()
    var nicknameInput by remember { mutableStateOf("") }
    var useSslInput by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Nuevo LaunchedEffect para asegurar que el drawer esté cerrado al conectar
    LaunchedEffect(connectionState) {
        if (connectionState) {
            // Si el drawer está abierto cuando se establece la conexión, ciérralo.
            if (drawerState.currentValue == DrawerValue.Open) {
                scope.launch {
                    drawerState.close()
                    Log.d("MainScreen", "Drawer programmatically closed upon connection.")
                }
            }
        }
    }

    // Collect unread targets to pass to AppDrawerContent
    val unreadTargetsState = viewModel.chatScreenState.unreadTargets.collectAsState()

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
                    onJoinChannelRequest = { 
                        scope.launch { drawerState.close() }
                    },
                    onOpenPrivateMessageRequest = { 
                        scope.launch { drawerState.close() }
                    },
                    onCloseTargetAction = {
                        viewModel.closeTarget(it)
                    },
                    onSettingsClick = { 
                        scope.launch { drawerState.close() }
                        navController.navigate("settings") // NAVEGACIÓN: Usar navController
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
                    Text(stringResource(R.string.status_not_connected), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (connectionState) {
                    ChatTopAppBar(
                        activeTarget = viewModel.chatScreenState.activeTarget.collectAsState().value,
                        onNavigationIconClick = { scope.launch { drawerState.open() } },
                        onDisconnectClick = { viewModel.disconnectFromServerAndStopService() }, 
                        onJoinChannelRequest = { channelName -> viewModel.joinChannel(channelName) },
                        onOpenPrivateMessageRequest = { nick -> viewModel.openPrivateMessage(nick) }
                    )
                } else {
                    DisconnectedTopAppBar()
                }
            },
            bottomBar = {
                val activeTargetValue = viewModel.chatScreenState.activeTarget.collectAsState().value
                val serverString = stringResource(R.string.cd_server) 
                if (connectionState && activeTargetValue != null && activeTargetValue != serverString) {
                    MessageInputSection(
                        onSendMessage = { message -> viewModel.sendMessage(message) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            modifier = modifier
        ) {paddingValues -> // Renamed `it` to `paddingValues` for clarity
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues) // Use paddingValues from Scaffold
            ) {
                if (!connectionState) {
                    ConnectionSetupSection(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        nickname = nicknameInput,
                        onNicknameChange = { nicknameInput = it },
                        useSsl = useSslInput,
                        onUseSslChange = { useSslInput = it },
                        onConnect = {
                            Log.d("MainScreen", "onConnect: Nick: $nicknameInput, SSL: $useSslInput")
                            if (nicknameInput.isNotBlank()) {
                                Log.d("MainScreen", "Llamando a viewModel.connect...")
                                viewModel.connect(
                                    nickname = nicknameInput,
                                    ssl = useSslInput
                                )
                            } else {
                                Log.w("MainScreen", "Nickname vacío, no se llama a viewModel.connect.")
                            }
                        }
                    )
                } else {
                    val activeTarget = viewModel.chatScreenState.activeTarget.collectAsState().value
                    val messages = viewModel.chatScreenState.uiMessages.collectAsState().value

                    if (activeTarget != null) {
                        MessagesList(messages = messages)
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(stringResource(R.string.prompt_select_channel_or_conversation), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionSetupSection(
    modifier: Modifier = Modifier,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    useSsl: Boolean,
    onUseSslChange: (Boolean) -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.connection_setup_title_simplified),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            label = { Text(stringResource(R.string.label_nickname)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = { onConnect() })
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUseSslChange(!useSsl) }
                .padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = useSsl,
                onCheckedChange = null
            )
            Text(
                text = stringResource(R.string.checkbox_label_connect_securely),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(stringResource(R.string.button_connect))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopAppBar(
    activeTarget: String?,
    onNavigationIconClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onJoinChannelRequest: (String) -> Unit,
    onOpenPrivateMessageRequest: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showJoinChannelDialog by remember { mutableStateOf(false) }
    var showOpenPmDialog by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(activeTarget ?: stringResource(R.string.app_title_default)) },
        navigationIcon = {
            IconButton(onClick = onNavigationIconClick) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_open_navigation_menu))
            }
        },
        actions = {
            IconButton(onClick = { showMenu = !showMenu }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_item_join_channel)) },
                    onClick = {
                        showMenu = false
                        showJoinChannelDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_item_private_message_to)) },
                    onClick = {
                        showMenu = false
                        showOpenPmDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_item_disconnect)) },
                    onClick = {
                        showMenu = false
                        onDisconnectClick()
                    }
                )
            }
        }
    )

    if (showJoinChannelDialog) {
        InputDialog(
            title = stringResource(R.string.dialog_title_join_channel),
            label = stringResource(R.string.dialog_label_channel_name),
            onDismiss = { showJoinChannelDialog = false },
            onConfirm = { channelName ->
                if (channelName.isNotBlank()) {
                    onJoinChannelRequest(channelName)
                }
                showJoinChannelDialog = false
            }
        )
    }

    if (showOpenPmDialog) {
        InputDialog(
            title = stringResource(R.string.dialog_title_open_private_message),
            label = stringResource(R.string.dialog_label_user_nickname),
            onDismiss = { showOpenPmDialog = false },
            onConfirm = { nick ->
                if (nick.isNotBlank()) {
                    onOpenPrivateMessageRequest(nick)
                }
                showOpenPmDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisconnectedTopAppBar() {
    TopAppBar(
        title = { Text(stringResource(R.string.app_title_disconnected)) }
    )
}

@Composable
fun InputDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textState by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(textState) }) {
                Text(stringResource(R.string.button_accept))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

@Composable
fun AppDrawerContent(
    chatTargets: List<String>,
    activeTarget: String?,
    unreadTargets: Set<String>,
    currentNick: String,
    onTargetSelected: (String) -> Unit,
    onJoinChannelRequest: () -> Unit,
    onOpenPrivateMessageRequest: () -> Unit,
    onCloseTargetAction: (String) -> Unit,
    onSettingsClick: () -> Unit // Added onSettingsClick parameter
) {
    val serverString = stringResource(R.string.cd_server)

    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxHeight()) { // Wrap content in a Column
            Text(stringResource(R.string.drawer_title_channels_chats), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.drawer_user_connected_as, currentNick), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
            Divider()
            LazyColumn(modifier = Modifier.weight(1f)) { // Make LazyColumn take available space
                items(chatTargets.distinct()) { target ->
                    val isServerTarget = target == serverString
                    val isUnread = unreadTargets.contains(target)
                    val fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal

                    NavigationDrawerItem(
                        icon = {
                            when {
                                target.startsWith("#") -> Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_channel))
                                isServerTarget -> Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_server))
                                else -> Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.cd_private_message))
                            }
                        },
                        label = { 
                            Text(
                                text = if (isServerTarget) serverString else target, 
                                fontWeight = fontWeight
                            )
                        },
                        selected = target == activeTarget,
                        onClick = { onTargetSelected(target) },
                        badge = {
                            if (!isServerTarget && target != activeTarget) {
                                IconButton(onClick = { onCloseTargetAction(target) }) {
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.cd_close_target, target))
                                }
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
            Divider() // Optional: Add a divider before the settings item
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.menu_item_settings)) },
                label = { Text(stringResource(R.string.menu_item_settings)) },
                selected = false, // Settings item is never "selected" in this context
                onClick = onSettingsClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}

@Composable
fun MessagesList(messages: List<UiChatMessage>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true // New messages appear at the bottom and list scrolls up
    ) {
        items(messages.reversed()) { msg -> // Reverse messages to show newest at bottom
            MessageRow(msg)
        }
    }
}

@Composable
fun MessageRow(message: UiChatMessage) {
    val textColor = when (message.type) {
        UiMessageType.SYSTEM_MESSAGE, UiMessageType.JOIN_PART_QUIT, UiMessageType.NICK_CHANGE, UiMessageType.MODE_CHANGE -> Color.Gray
        UiMessageType.SERVER_INFO -> Color.DarkGray
        UiMessageType.NOTICE -> Color(0xFFFFA500) // Orange color for notices
        else -> MaterialTheme.colorScheme.onSurface
    }
    val fontStyle = if (message.type == UiMessageType.SYSTEM_MESSAGE || message.type == UiMessageType.SERVER_INFO) FontStyle.Italic else FontStyle.Normal
    val fontWeight = if (message.isOwnMessage) FontWeight.Bold else FontWeight.Normal

    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()) {
        Text(
            text = message.fullText, 
            color = textColor, 
            fontStyle = fontStyle, 
            fontWeight = if (message.sender == null || message.type == UiMessageType.CHANNEL_MSG_SENT || message.type == UiMessageType.PRIVATE_MSG_SENT) fontWeight else FontWeight.Normal, // Apply bold only to own messages or if sender is null
            fontSize = 14.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInputSection(
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textState by remember { mutableStateOf("") }

    OutlinedTextField(
        value = textState,
        onValueChange = { textState = it },
        label = { Text(stringResource(R.string.label_write_message)) },
        trailingIcon = {
            IconButton(onClick = {
                if (textState.isNotBlank()) {
                    onSendMessage(textState)
                    textState = ""
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.cd_send_message))
            }
        },
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth(),
        singleLine = false, // Allow multiple lines for longer messages
        maxLines = 3 // Limit to 3 lines, then scrolls
    )
}

// This composable seems to be a duplicate or an older version of what's handled by MainScreen's main content area.
// Consider removing if not used or refactoring.
/*
@Composable
fun ChatContentSection(
    uiMessages: List<UiChatMessage>,
    chatTargets: List<String>,
    activeTarget: String?,
    onTargetSelected: (String) -> Unit,
    onCloseTarget: (String) -> Unit,
    currentNick: String,
    onJoinChannelRequest: (String) -> Unit,
    onOpenPrivateMessageRequest: (String) -> Unit
) {
    if (activeTarget != null) {
        MessagesList(messages = uiMessages)
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.prompt_select_channel_or_conversation), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
*/
