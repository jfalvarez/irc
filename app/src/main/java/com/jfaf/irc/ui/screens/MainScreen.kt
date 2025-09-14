package com.jfaf.irc.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer // Added import
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.jfaf.irc.R
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.ui.viewmodels.UiChatMessage
import com.jfaf.irc.ui.viewmodels.UiMessageType
import kotlinx.coroutines.launch
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.DividerDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
// Removed scaleIn and scaleOut as AnimatedVisibility for image itself is removed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
    navController: NavHostController
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
                    val activeTargetValue = viewModel.chatScreenState.activeTarget.collectAsState().value
                    val serverString = stringResource(R.string.cd_server)
                    if (connectionState && activeTargetValue != null && activeTargetValue != serverString) {
                        MessageInputSection(
                            onSendMessage = { viewModel.sendMessage(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                modifier = modifier
            ) { paddingValues ->
                AnimatedContent(
                    targetState = connectionState,
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
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
                        )
                    } else {
                        val showUserListState by viewModel.chatScreenState.showUserList.collectAsState()
                        Row(modifier = Modifier.fillMaxSize().background(IRCTheme.gradientBrush)) {
                            Box(modifier = Modifier.weight(if (showUserListState) 0.6f else 1f)) {
                                val activeTarget = viewModel.chatScreenState.activeTarget.collectAsState().value
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
                            if (showUserListState) {
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

@Composable
fun ConnectionSetupSection(
    nickname: String, onNicknameChange: (String) -> Unit,
    useSsl: Boolean, onUseSslChange: (Boolean) -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(IRCTheme.gradientBrush).padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("IRC Connect", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Conéctate y empieza a chatear", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            TextField(
                value = nickname, onValueChange = onNicknameChange, modifier = Modifier.fillMaxWidth(),
                label = { Text("Tu Nickname") },
                leadingIcon = { Icon(Icons.Filled.Person, "Nickname Icon") },
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onPrimary, unfocusedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), cursorColor = MaterialTheme.colorScheme.onPrimary,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                    focusedLabelColor = MaterialTheme.colorScheme.onPrimary, unfocusedLabelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    focusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary, unfocusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                ),
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onConnect() })
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onUseSslChange(!useSsl) }.padding(vertical = 4.dp)) {
                Checkbox(checked = useSsl, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.onPrimary, checkmarkColor = MaterialTheme.colorScheme.primary, uncheckedColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)))
                Text(stringResource(R.string.checkbox_label_connect_securely), color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Button(
            onClick = onConnect, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(stringResource(R.string.button_connect), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopAppBar(
    activeTarget: String?, onNavigationIconClick: () -> Unit, onDisconnectClick: () -> Unit,
    onJoinChannelRequest: (String) -> Unit, onOpenPrivateMessageRequest: (String) -> Unit,
    onToggleUserList: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showJoinChannelDialog by remember { mutableStateOf(false) }
    var showOpenPmDialog by remember { mutableStateOf(false) }

    // Determinar si el target activo es un canal
    val isChannel = activeTarget?.startsWith("#") == true

    TopAppBar(
        title = { Text(activeTarget ?: stringResource(R.string.app_title_default)) },
        navigationIcon = { IconButton(onClick = onNavigationIconClick) { Icon(Icons.Filled.Menu, stringResource(R.string.cd_open_navigation_menu)) } },
        actions = {
            // Mostrar el botón de lista de usuarios SOLO si es un canal
            if (isChannel) {
                IconButton(onClick = onToggleUserList) { Icon(Icons.Filled.Person, stringResource(R.string.cd_toggle_user_list)) }
            }
            IconButton(onClick = { showMenu = !showMenu }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_more_options)) }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(IRCTheme.dropdownMenuContainerOpaque)) {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_join_channel), color = MaterialTheme.colorScheme.onSurface) }, onClick = { showMenu = false; showJoinChannelDialog = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_private_message_to), color = MaterialTheme.colorScheme.onSurface) }, onClick = { showMenu = false; showOpenPmDialog = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_disconnect), color = MaterialTheme.colorScheme.onSurface) }, onClick = { showMenu = false; onDisconnectClick() })
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary)
    )
    if (showJoinChannelDialog) {
        InputDialog(stringResource(R.string.dialog_title_join_channel), stringResource(R.string.dialog_label_channel_name), { showJoinChannelDialog = false }) { channelName ->
            if (channelName.isNotBlank()) onJoinChannelRequest(if (channelName.startsWith("#")) channelName else "#$channelName")
            showJoinChannelDialog = false
        }
    }
    if (showOpenPmDialog) {
        InputDialog(stringResource(R.string.dialog_title_open_private_message), stringResource(R.string.dialog_label_user_nickname), { showOpenPmDialog = false }) { nick ->
            if (nick.isNotBlank()) onOpenPrivateMessageRequest(nick)
            showOpenPmDialog = false
        }
    }
}

@Composable
fun InputDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var textState by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = { OutlinedTextField(value = textState, onValueChange = { textState = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, cursorColor = MaterialTheme.colorScheme.primary, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedLabelColor = MaterialTheme.colorScheme.onSurface, unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, focusedContainerColor = IRCTheme.outlinedTextFieldContainer, unfocusedContainerColor = IRCTheme.outlinedTextFieldContainer)) },
        confirmButton = { Button(onClick = { onConfirm(textState) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary)) { Text(stringResource(R.string.button_accept)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)) } },
        containerColor = IRCTheme.dialogContainerOpaque
    )
}

@Composable
fun AppDrawerContent(
    chatTargets: List<String>, activeTarget: String?, unreadTargets: Set<String>, currentNick: String,
    onTargetSelected: (String) -> Unit, onJoinChannelRequest: () -> Unit, onOpenPrivateMessageRequest: () -> Unit,
    onCloseTargetAction: (String) -> Unit, onSettingsClick: () -> Unit
) {
    val serverString = stringResource(R.string.cd_server)
    ModalDrawerSheet(drawerContainerColor = IRCTheme.drawerContainerOpaque) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(stringResource(R.string.drawer_title_channels_chats), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.drawer_user_connected_as, currentNick), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            HorizontalDivider(Modifier, DividerDefaults.Thickness, MaterialTheme.colorScheme.surfaceVariant)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(chatTargets.distinct()) { target ->
                    val isServerTarget = target == serverString
                    val isUnread = unreadTargets.contains(target)
                    val fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                    val isSelected = target == activeTarget
                    NavigationDrawerItem(
                        icon = {
                            val iconColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            when {
                                target.startsWith("#") -> Icon(Icons.Filled.Add, stringResource(R.string.cd_channel), tint = iconColor)
                                isServerTarget -> Icon(Icons.Filled.Menu, stringResource(R.string.cd_server), tint = iconColor)
                                else -> Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.cd_private_message), tint = iconColor)
                            }
                        },
                        label = { Text(if (isServerTarget) serverString else target, fontWeight = fontWeight, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)) },
                        selected = isSelected, onClick = { onTargetSelected(target) },
                        badge = {
                            if (!isServerTarget && target != activeTarget) {
                                IconButton(onClick = { onCloseTargetAction(target) }) { Icon(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.cd_close_target, target), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), unselectedContainerColor = Color.Transparent, selectedIconColor = MaterialTheme.colorScheme.onPrimary, unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), selectedTextColor = MaterialTheme.colorScheme.onPrimary, unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)),
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            val settingsSelected = false
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Settings, stringResource(R.string.menu_item_settings), tint = if (settingsSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)) },
                label = { Text(stringResource(R.string.menu_item_settings), color = if (settingsSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)) },
                selected = settingsSelected, onClick = onSettingsClick,
                colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), unselectedContainerColor = Color.Transparent, selectedIconColor = MaterialTheme.colorScheme.onPrimary, unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), selectedTextColor = MaterialTheme.colorScheme.onPrimary, unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)),
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}

@Composable
fun MessagesList(messages: List<UiChatMessage>, onImageClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), reverseLayout = true, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
        items(messages.reversed()) { msg -> MessageRow(msg, onImageClick) }
    }
}

@Composable
fun MessageRow(message: UiChatMessage, onImageClick: (String) -> Unit) {
    val baseTextColor = when (message.type) {
        UiMessageType.SYSTEM_MESSAGE, UiMessageType.JOIN_PART_QUIT, UiMessageType.NICK_CHANGE, UiMessageType.MODE_CHANGE -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        UiMessageType.SERVER_INFO -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
        UiMessageType.NOTICE -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val fontStyle = when (message.type) {
        UiMessageType.SYSTEM_MESSAGE, UiMessageType.JOIN_PART_QUIT, UiMessageType.NICK_CHANGE, UiMessageType.MODE_CHANGE, UiMessageType.SERVER_INFO, UiMessageType.NOTICE -> FontStyle.Italic
        else -> FontStyle.Normal
    }
    val fontWeight = if (message.isOwnMessage && message.annotatedString?.spanStyles?.all { it.item.fontWeight != FontWeight.Bold } == true) FontWeight.Bold else FontWeight.Normal

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()) {
        Text(message.annotatedString ?: AnnotatedString(message.fullText), color = baseTextColor, fontStyle = fontStyle, fontWeight = fontWeight, fontSize = 14.sp)
        if (!message.imageUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            AsyncImage(
                model = message.imageUrl,
                contentDescription = "Imagen adjunta: ${message.imageUrl}",
                modifier = Modifier.fillMaxWidth().height(200.dp).clickable { if (!message.imageUrl.isNullOrBlank()) onImageClick(message.imageUrl) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInputSection(onSendMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.primary, shadowElevation = 4.dp) {
        var textState by remember { mutableStateOf("") }
        val keyboardController = LocalSoftwareKeyboardController.current
        TextField(
            value = textState, onValueChange = { textState = it },
            placeholder = { Text(stringResource(R.string.label_write_message), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)) },
            trailingIcon = { IconButton(onClick = { if (textState.isNotBlank()) { onSendMessage(textState); textState = "" } }) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.cd_send_message), tint = MaterialTheme.colorScheme.onPrimary) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (textState.isNotBlank()) { onSendMessage(textState); textState = ""; keyboardController?.hide() } }),
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onPrimary, unfocusedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                focusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f), unfocusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                disabledContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f), cursorColor = MaterialTheme.colorScheme.onPrimary,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                focusedLabelColor = MaterialTheme.colorScheme.onPrimary, unfocusedLabelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f), unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            )
        )
    }
}

@Composable
fun FullScreenImageViewer(imageUrl: String, onClose: () -> Unit) {
    // Remove testImageUrl, use imageUrl directly
    Log.d("FullScreenImageViewer", "Displaying image. URL: $imageUrl")

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        var imagePainterState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl, // Use the actual imageUrl parameter
                contentDescription = stringResource(R.string.cd_full_screen_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(8.dp))
                    .graphicsLayer { // Controlar alfa basado en el estado
                        alpha = if (imagePainterState is AsyncImagePainter.State.Success) 1f else 0f
                    }
                    .clickable( // Consumir clics en el área de la imagen
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* No hacer nada para evitar cerrar el diálogo al tocar la imagen */ }
                    ),
                onState = { state ->
                    Log.d("FullScreenImageViewer", "AsyncImage State changed: $state for URL: $imageUrl") // Log with actual imageUrl
                    imagePainterState = state // Actualizar el estado
                }
            )

            // UI condicional para estados de carga y error
            when (val currentState = imagePainterState) {
                is AsyncImagePainter.State.Loading -> {
                    Log.d("FullScreenImageViewer", "Showing loading indicator because painterState is Loading.")
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                }
                is AsyncImagePainter.State.Error -> {
                    Log.e("FullScreenImageViewer", "Showing error message. Error: ${currentState.result.throwable}")
                    Text(
                        text = stringResource(R.string.error_loading_image),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                            .padding(16.dp)
                    )
                }
                is AsyncImagePainter.State.Success -> {
                    Log.d("FullScreenImageViewer", "Image successfully loaded. DataSource: ${currentState.result.dataSource}")
                    // No es necesario hacer nada aquí, AsyncImage ya es visible a través de su alfa
                }
                is AsyncImagePainter.State.Empty -> {
                     Log.d("FullScreenImageViewer", "Painter state is Empty. Showing loading indicator as a fallback.")
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
