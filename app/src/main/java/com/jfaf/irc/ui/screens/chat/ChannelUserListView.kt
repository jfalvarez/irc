package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jfaf.irc.R
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.ui.viewmodels.MainViewModel

@Composable
fun ChannelUserListView(
    mainViewModel: MainViewModel
) {
    val userList by mainViewModel.chatScreenState.currentChannelUserList.collectAsState()
    val activeTarget by mainViewModel.chatScreenState.activeTarget.collectAsState()
    var expandedUserMenu by remember { mutableStateOf<String?>(null) }

    if (activeTarget?.startsWith("#") == true) {
        if (userList.isEmpty()) {
            Text(
                text = stringResource(R.string.no_users_in_channel),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(userList) { userName ->
                    Box { // Needed for DropdownMenu positioning
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedUserMenu = userName } // Toda la fila es clickeable
                                .padding(vertical = 8.dp, horizontal = 16.dp), // Aumentado padding vertical para mejor toque
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = userName,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        DropdownMenu(
                            expanded = expandedUserMenu == userName,
                            onDismissRequest = { expandedUserMenu = null },
                            modifier = Modifier.background(IRCTheme.dropdownMenuContainerOpaque) // Fondo aplicado directamente
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        stringResource(R.string.action_send_private_message),
                                        color = MaterialTheme.colorScheme.onPrimary // Color de texto explícito
                                    )
                                },
                                onClick = {
                                    mainViewModel.openPrivateMessage(userName)
                                    expandedUserMenu = null
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        stringResource(R.string.action_ignore_user),
                                        color = MaterialTheme.colorScheme.onPrimary // Color de texto explícito
                                    )
                                },
                                onClick = {
                                    mainViewModel.ignoreUser(userName)
                                    expandedUserMenu = null
                                }
                            )
                        }
                    }
                }
            }
        }
    } else {
        Text(
            text = stringResource(R.string.select_channel_to_see_users),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
