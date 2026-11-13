package com.jfaf.irc.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.jfaf.irc.R
import com.jfaf.irc.data.prefs.ServerConfig
import com.jfaf.irc.ui.common.ThemedOutlinedTextField
import com.jfaf.irc.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val showJoinPartQuitMessages by viewModel.showJoinPartQuitMessages.collectAsState()
    val showNickChanges by viewModel.showNickChanges.collectAsState()
    val showModeChanges by viewModel.showModeChanges.collectAsState()
    val showPingPongMessages by viewModel.showPingPongMessages.collectAsState()
    val showMediaPreviews by viewModel.showMediaPreviews.collectAsState()
    val ignoredUsers by viewModel.ignoredUsers.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val serverConfig by viewModel.serverConfig.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_item_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_up)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            item {
                ServerConfigSection(
                    config = serverConfig,
                    onSave = { host, plain, ssl -> viewModel.saveServerConfig(host, plain, ssl) },
                    onReset = { viewModel.resetServerConfig() }
                )
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            item {
                Text(
                    text = stringResource(R.string.settings_section_message_preferences),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            item {
                SettingRowWithCheckbox(
                    text = stringResource(R.string.settings_option_show_join_part_quit),
                    checked = showJoinPartQuitMessages,
                    onCheckedChange = { viewModel.setShowJoinPartQuitMessages(it) }
                )
            }
            item {
                SettingRowWithCheckbox(
                    text = stringResource(R.string.settings_option_show_nick_changes),
                    checked = showNickChanges,
                    onCheckedChange = { viewModel.setShowNickChanges(it) }
                )
            }
            item {
                SettingRowWithCheckbox(
                    text = stringResource(R.string.settings_option_show_mode_changes),
                    checked = showModeChanges,
                    onCheckedChange = { viewModel.setShowModeChanges(it) }
                )
            }
            item {
                SettingRowWithCheckbox(
                    text = stringResource(R.string.settings_option_show_ping_messages),
                    checked = showPingPongMessages,
                    onCheckedChange = { viewModel.setShowPingPongMessages(it) }
                )
            }
            item {
                SettingRowWithCheckbox(
                    text = stringResource(R.string.settings_option_show_media_previews),
                    checked = showMediaPreviews,
                    onCheckedChange = { viewModel.setShowMediaPreviews(it) }
                )
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            item {
                CollapsibleUserListSection(
                    sectionTitle = stringResource(R.string.settings_section_friends),
                    users = friends,
                    onAddUser = { viewModel.addFriend(it) },
                    onRemoveUser = { viewModel.removeFriend(it) },
                    addUserInputLabel = stringResource(R.string.settings_friend_user_dialog_label),
                    addButtonText = stringResource(R.string.settings_button_add_friend),
                    removeButtonText = stringResource(R.string.settings_button_remove_friend),
                    noUsersText = stringResource(R.string.settings_no_friends)
                )
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            item {
                CollapsibleUserListSection(
                    sectionTitle = stringResource(R.string.settings_section_ignored_users),
                    users = ignoredUsers,
                    onAddUser = { viewModel.addIgnoredUser(it) },
                    onRemoveUser = { viewModel.removeIgnoredUser(it) },
                    addUserInputLabel = stringResource(R.string.settings_ignore_user_dialog_label),
                    addButtonText = stringResource(R.string.settings_button_add_ignored),
                    removeButtonText = stringResource(R.string.settings_button_unignore),
                    noUsersText = stringResource(R.string.settings_no_ignored_users)
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CollapsibleUserListSection(
    sectionTitle: String,
    users: Set<String>,
    onAddUser: (String) -> Unit,
    onRemoveUser: (String) -> Unit,
    addUserInputLabel: String,
    addButtonText: String,
    removeButtonText: String,
    noUsersText: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    var userInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand"
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemedOutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        label = { Text(addUserInputLabel) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (userInput.isNotBlank()) {
                                onAddUser(userInput)
                                userInput = ""
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        }
                    ) {
                        Text(addButtonText)
                    }
                }

                if (users.isEmpty()) {
                    Text(
                        text = noUsersText,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    users.toList().sorted().forEach { nick ->
                        UserRow(
                            nick = nick,
                            onRemoveClicked = { onRemoveUser(nick) },
                            removeButtonText = removeButtonText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    nick: String,
    onRemoveClicked: () -> Unit,
    removeButtonText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = nick,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = onRemoveClicked) {
            Text(removeButtonText)
        }
    }
}

@Composable
private fun SettingRowWithCheckbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ServerConfigSection(
    config: ServerConfig,
    onSave: (host: String, plainPort: String, sslPort: String) -> Boolean,
    onReset: () -> Unit
) {
    // Se reinician los campos cuando cambia la configuración guardada (p. ej. tras "Restaurar").
    var host by remember(config) { mutableStateOf(config.host) }
    var plainPort by remember(config) { mutableStateOf(config.plainPort.toString()) }
    var sslPort by remember(config) { mutableStateOf(config.sslPort.toString()) }
    var feedback by remember { mutableStateOf<Int?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_section_server),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp)
        )
        ThemedOutlinedTextField(
            value = host,
            onValueChange = { host = it; feedback = null },
            label = { Text(stringResource(R.string.settings_server_host_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemedOutlinedTextField(
                value = plainPort,
                onValueChange = { plainPort = it; feedback = null },
                label = { Text(stringResource(R.string.settings_server_plain_port_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            ThemedOutlinedTextField(
                value = sslPort,
                onValueChange = { sslPort = it; feedback = null },
                label = { Text(stringResource(R.string.settings_server_ssl_port_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        feedback?.let {
            Text(
                text = stringResource(it),
                color = if (it == R.string.settings_server_invalid) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onBackground
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val ok = onSave(host, plainPort, sslPort)
                feedback = if (ok) R.string.settings_server_saved else R.string.settings_server_invalid
                if (ok) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            }) { Text(stringResource(R.string.settings_server_save)) }
            OutlinedButton(onClick = {
                onReset()
                feedback = null
            }) { Text(stringResource(R.string.settings_server_reset)) }
        }
    }
}
