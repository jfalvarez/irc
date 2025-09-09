package com.jfaf.irc.ui.screens

// import android.widget.Toast // No más Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons 
import androidx.compose.material.icons.filled.Close 
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// import androidx.compose.ui.platform.LocalContext // No más LocalContext para Toast
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.ui.viewmodels.UiChatMessage
import com.jfaf.irc.ui.viewmodels.UiMessageType
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions 
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val uiMessages by viewModel.uiMessages.collectAsState()
    val chatTargetsState by viewModel.chatTargets.collectAsState()
    val activeTargetState by viewModel.activeTarget.collectAsState()

    var nicknameInput by remember { mutableStateOf("") }
    var useSslInput by remember { mutableStateOf(false) }

    var newTargetInputText by remember { mutableStateOf("") }
    var messageToSend by remember { mutableStateOf("") }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() } // Para el Snackbar

    LaunchedEffect(uiMessages) {
        if (uiMessages.isNotEmpty()) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(uiMessages.size - 1)
            }
        }
    }

    // Observar eventos de mensajes del ViewModel para Snackbars
    LaunchedEffect(key1 = viewModel.userMessageEvents) {
        viewModel.userMessageEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) }, // Añadir SnackbarHost
        topBar = {
            TopAppBar(title = { Text("IRC Client - ${activeTargetState ?: ""}") })
        },
        bottomBar = {
            if (connectionState && activeTargetState != null) {
                MessageInputSection(
                    message = messageToSend,
                    onMessageChange = { messageToSend = it },
                    onSendMessage = {
                        if (messageToSend.isNotBlank()) {
                            viewModel.sendMessage(messageToSend)
                            messageToSend = ""
                        }
                    },
                    activeTargetLabel = activeTargetState ?: ""
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                // Sección cuando está conectado
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Conectado como: ${viewModel.currentNickname}", 
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f) 
                        )
                        Button(
                            onClick = { viewModel.disconnectFromServerAndStopService() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Desconectar")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTargetInputText,
                            onValueChange = { newTargetInputText = it },
                            label = { Text("Abrir Canal/Nick") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newTargetInputText.isNotBlank()) {
                                    if (newTargetInputText.startsWith("#")) {
                                        viewModel.joinChannel(newTargetInputText)
                                    } else {
                                        viewModel.openPrivateMessage(newTargetInputText)
                                    }
                                    newTargetInputText = "" 
                                }
                            },
                            enabled = newTargetInputText.isNotBlank()
                        ) {
                            Text("Abrir")
                        }
                    }
                }
                
                val currentTargets = chatTargetsState
                val currentActive = activeTargetState

                if (currentTargets.isNotEmpty()) {
                    var initialTabIndex = currentTargets.indexOf(currentActive)
                    if (initialTabIndex == -1 && currentTargets.isNotEmpty()) { 
                        initialTabIndex = 0 
                    }
                    val safeTabIndex = if (currentTargets.isEmpty()) 0 else initialTabIndex.coerceIn(0, currentTargets.size - 1)

                    if (currentTargets.isNotEmpty()){ 
                        key(currentTargets) { 
                            ScrollableTabRow(
                                selectedTabIndex = safeTabIndex,
                                edgePadding = 0.dp
                            ) {
                                currentTargets.forEach { targetName ->
                                    key(targetName) { 
                                        Tab(
                                            selected = currentActive == targetName,
                                            onClick = { 
                                                viewModel.setActiveTarget(targetName)
                                            },
                                            content = { 
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp) 
                                                ) {
                                                    Text(targetName, style = MaterialTheme.typography.bodyMedium)
                                                    if (targetName != "Servidor") {
                                                        Spacer(Modifier.width(6.dp)) 
                                                        Icon(
                                                            imageVector = Icons.Filled.Close,
                                                            contentDescription = "Cerrar pestaña $targetName",
                                                            modifier = Modifier
                                                                .size(18.dp) 
                                                                .clickable(onClickLabel = "Cerrar pestaña") { 
                                                                    viewModel.closeTarget(targetName)
                                                                },
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        ) 
                                    }
                                }
                            }
                        }
                    }
                } 

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp)
                ) {
                    items(uiMessages) { chatMessage ->
                        val textColor = when (chatMessage.type) {
                            UiMessageType.CHANNEL_MSG_SENT -> MaterialTheme.colorScheme.primary
                            UiMessageType.CHANNEL_MSG_RECEIVED -> MaterialTheme.colorScheme.onSurface
                            UiMessageType.PRIVATE_MSG_SENT -> MaterialTheme.colorScheme.primary
                            UiMessageType.PRIVATE_MSG_RECEIVED -> MaterialTheme.colorScheme.onSurface
                            UiMessageType.JOIN_PART_QUIT,
                            UiMessageType.NICK_CHANGE,
                            UiMessageType.MODE_CHANGE -> MaterialTheme.colorScheme.onSurfaceVariant
                            UiMessageType.NOTICE -> MaterialTheme.colorScheme.onSurfaceVariant 
                            UiMessageType.SYSTEM_MESSAGE -> MaterialTheme.colorScheme.onSurfaceVariant
                            UiMessageType.SERVER_INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                            UiMessageType.OTHER_COMMAND,
                            UiMessageType.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val fontStyle = when (chatMessage.type) {
                            UiMessageType.PRIVATE_MSG_SENT,
                            UiMessageType.PRIVATE_MSG_RECEIVED,
                            UiMessageType.JOIN_PART_QUIT,
                            UiMessageType.SERVER_INFO,
                            UiMessageType.NICK_CHANGE,
                            UiMessageType.MODE_CHANGE,
                            UiMessageType.SYSTEM_MESSAGE,
                            UiMessageType.NOTICE -> FontStyle.Italic
                            else -> FontStyle.Normal
                        }
                        Text(
                            text = chatMessage.fullText,
                            color = textColor,
                            fontStyle = fontStyle,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
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
            "CONEXIÓN IRC - SIMPLIFICADA", 
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        OutlinedTextField(
            value = nickname, 
            onValueChange = onNicknameChange, 
            label = { Text("Nickname") }, 
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
                text = "Usar SSL/TLS",
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp)) 
        Button(
            onClick = onConnect, 
            modifier = Modifier.fillMaxWidth().height(48.dp) 
        ) {
            Text("Conectar")
        }
    }
}

@Composable
fun MessageInputSection(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    activeTargetLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            label = { Text("Mensaje a ${activeTargetLabel.ifEmpty { "..." }}") },
            modifier = Modifier.weight(1f),
            singleLine = true, 
            keyboardActions = KeyboardActions(onSend = { onSendMessage() }) 
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onSendMessage) {
            Text("Enviar")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            var nickname by remember { mutableStateOf("TestUser") }
            var ssl by remember { mutableStateOf(false) }
            ConnectionSetupSection(
                nickname = nickname,
                onNicknameChange = { nickname = it },
                useSsl = ssl,
                onUseSslChange = { ssl = !ssl },
                onConnect = {}
            )
        }
    }
}
