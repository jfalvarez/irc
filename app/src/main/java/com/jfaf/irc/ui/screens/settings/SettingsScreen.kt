package com.jfaf.irc.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jfaf.irc.R
import com.jfaf.irc.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val showJoinPartQuitMessages by viewModel.showJoinPartQuitMessages.collectAsState()
    val showNickChanges by viewModel.showNickChanges.collectAsState()
    val showModeChanges by viewModel.showModeChanges.collectAsState()
    val showPingPongMessages by viewModel.showPingPongMessages.collectAsState()
    val showMediaPreviews by viewModel.showMediaPreviews.collectAsState()
    val ignoredUsers by viewModel.ignoredUsers.collectAsState()
    val friends by viewModel.friends.collectAsState()

    var nickToIgnoreInput by remember { mutableStateOf("") }
    var nickToFriendInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

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
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
                item { // Nueva opción para previsualizaciones de medios
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
                    Text(
                        text = stringResource(R.string.settings_section_ignored_users),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = nickToIgnoreInput,
                            onValueChange = { nickToIgnoreInput = it },
                            label = { Text(stringResource(R.string.settings_ignore_user_dialog_label)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (nickToIgnoreInput.isNotBlank()) {
                                    viewModel.addIgnoredUser(nickToIgnoreInput)
                                    nickToIgnoreInput = ""
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.settings_button_add_ignored))
                        }
                    }
                }

                if (ignoredUsers.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.settings_no_ignored_users),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(ignoredUsers.toList().sorted()) { nick ->
                        IgnoredUserRow(
                            nick = nick,
                            onUnignoreClicked = {
                                viewModel.removeIgnoredUser(nick)
                            }
                        )
                    }
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.settings_section_friends),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = nickToFriendInput,
                            onValueChange = { nickToFriendInput = it },
                            label = { Text(stringResource(R.string.settings_friend_user_dialog_label)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (nickToFriendInput.isNotBlank()) {
                                    viewModel.addFriend(nickToFriendInput)
                                    nickToFriendInput = ""
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.settings_button_add_friend))
                        }
                    }
                }

                if (friends.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.settings_no_friends),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(friends.toList().sorted()) { nick ->
                        FriendRow(
                            nick = nick,
                            onRemoveClicked = {
                                viewModel.removeFriend(nick)
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
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
private fun IgnoredUserRow(
    nick: String,
    onUnignoreClicked: () -> Unit,
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
        Button(
            onClick = onUnignoreClicked
        ) {
            Text(stringResource(R.string.settings_button_unignore))
        }
    }
}

@Composable
private fun FriendRow(
    nick: String,
    onRemoveClicked: () -> Unit,
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
        Button(
            onClick = onRemoveClicked
        ) {
            Text(stringResource(R.string.settings_button_remove_friend))
        }
    }
}
