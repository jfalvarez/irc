package com.jfaf.irc.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val ignoredUsers by viewModel.ignoredUsers.collectAsState()

    var nickToIgnoreInput by remember { mutableStateOf("") }
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
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp) // Only horizontal padding for the main column
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth()){
                item {
                    Text(
                        text = stringResource(R.string.settings_section_message_preferences),
                        style = MaterialTheme.typography.titleMedium, // Changed to titleMedium
                        modifier = Modifier.padding(vertical = 16.dp) // Adjusted padding
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                }

                item {
                    Text(
                        text = stringResource(R.string.settings_section_ignored_users),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp) // Adjusted padding
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
                        Button(onClick = {
                            if (nickToIgnoreInput.isNotBlank()) {
                                viewModel.addIgnoredUser(nickToIgnoreInput)
                                // Potentially show snackbar for R.string.settings_ignored_user_added
                                nickToIgnoreInput = "" // Clear input
                                keyboardController?.hide() // Hide keyboard
                                focusManager.clearFocus() // Clear focus
                            } else {
                                // Potentially show snackbar for R.string.settings_error_nick_empty
                            }
                        }) {
                            Text(stringResource(R.string.settings_button_add_ignored))
                        }
                    }
                }

                if (ignoredUsers.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.settings_no_ignored_users),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(ignoredUsers.toList().sorted()) { nick -> // Sort for consistent order
                        IgnoredUserRow(
                            nick = nick,
                            onUnignoreClicked = {
                                viewModel.removeIgnoredUser(nick)
                                // Potentially show snackbar for R.string.settings_ignored_user_removed
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) } // Add some space at the bottom
            }
        }
    }
}

@Composable
private fun SettingRowWithCheckbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier // Added modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 16.dp), // Added horizontal padding here if removed from main column
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
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
    modifier: Modifier = Modifier // Added modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp), // Added horizontal padding here if removed from main column
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = nick, modifier = Modifier.weight(1f))
        Button(onClick = onUnignoreClicked) {
            Text(stringResource(R.string.settings_button_unignore))
        }
    }
}
