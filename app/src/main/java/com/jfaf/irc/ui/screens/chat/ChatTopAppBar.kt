package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jfaf.irc.R

import com.jfaf.irc.ui.screens.chat.InputDialog
import com.jfaf.irc.ui.theme.IRCTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopAppBar(
    activeTarget: String?,
    onNavigationIconClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onJoinChannelRequest: (String) -> Unit,
    onOpenPrivateMessageRequest: (String) -> Unit,
    onToggleUserList: () -> Unit,
    onIgnoreUserInPm: (nick: String) -> Unit,
    onAddFriendInPm: (nick: String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showJoinChannelDialog by remember { mutableStateOf(false) }
    var showOpenPmDialog by remember { mutableStateOf(false) }

    val isChannel = activeTarget?.startsWith("#") == true
    // Exclude SERVER_TARGET_ID (assumed to be "Servidor") from being a PM for the ignore option
    val isPm = activeTarget != null &&
               activeTarget.isNotBlank() &&
               !activeTarget.startsWith("#") &&
               activeTarget != "Servidor"

    TopAppBar(
        title = { Text(activeTarget ?: stringResource(R.string.app_title_default)) },
        navigationIcon = { IconButton(onClick = onNavigationIconClick) { Icon(Icons.Filled.Menu, stringResource(R.string.cd_open_navigation_menu)) } },
        actions = {
            if (isChannel) {
                IconButton(onClick = onToggleUserList) { Icon(Icons.Filled.Person, stringResource(R.string.cd_toggle_user_list)) }
            }
            IconButton(onClick = { showMenu = !showMenu }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_more_options)) }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(IRCTheme.dropdownMenuContainerOpaque)
            ) {
                if (isPm && activeTarget != null) { // activeTarget null check for safety
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_item_add_friend, activeTarget), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showMenu = false
                            onAddFriendInPm(activeTarget)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_item_ignore_user, activeTarget), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showMenu = false
                            onIgnoreUserInPm(activeTarget)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_item_join_channel), color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showMenu = false
                        showJoinChannelDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_item_private_message_to), color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showMenu = false
                        showOpenPmDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_item_disconnect), color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showMenu = false
                        onDisconnectClick()
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )

    if (showJoinChannelDialog) {
        InputDialog(
            title = stringResource(R.string.dialog_title_join_channel),
            label = stringResource(R.string.dialog_label_channel_name),
            onDismiss = { showJoinChannelDialog = false },
            onConfirm = { channelName ->
                if (channelName.isNotBlank()) {
                    onJoinChannelRequest(if (channelName.startsWith("#")) channelName else "#$channelName")
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
