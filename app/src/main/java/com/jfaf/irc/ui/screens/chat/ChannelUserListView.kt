package com.jfaf.irc.ui.screens.chat

import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.*
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.jfaf.irc.ui.viewmodels.MainViewModel

@Composable
fun ChannelUserListView(
    mainViewModel: MainViewModel
) {
    val userList by mainViewModel.chatScreenState.currentChannelUserList.collectAsState()
    val activeTarget by mainViewModel.chatScreenState.activeTarget.collectAsState()
    val ignoredUsers by mainViewModel.chatScreenState.ignoredUsers.collectAsState()
    var expandedUserMenu by remember { mutableStateOf<String?>(null) }

    val filteredUserList = userList.filterNot { user ->
        ignoredUsers.any { ignoredUser -> cleanNick(user).equals(ignoredUser, ignoreCase = true) }
    }

    if (activeTarget?.startsWith("#") == true) {
        if (filteredUserList.isEmpty()) {
            Text(
                text = stringResource(Res.string.no_users_in_channel),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredUserList) { userName ->
                    Box { // Needed for DropdownMenu positioning
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedUserMenu = userName } // Toda la fila es clickeable
                                .padding(vertical = 8.dp, horizontal = 16.dp), // Aumentado padding vertical para mejor toque
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = userName, // Display userName with prefix
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        UserActionsMenu(
                            userName = userName,
                            expanded = expandedUserMenu == userName,
                            onDismiss = { expandedUserMenu = null },
                            mainViewModel = mainViewModel
                        )
                    }
                }
            }
        }
    } else {
        Text(
            text = stringResource(Res.string.select_channel_to_see_users),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
