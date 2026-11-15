package com.jfaf.irc.ui.screens.chat

import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.jfaf.irc.domain.chat.UserListGrouping
import com.jfaf.irc.domain.chat.UserRole
import com.jfaf.irc.ui.theme.nickColor
import com.jfaf.irc.ui.viewmodels.MainViewModel

private val USER_AVATAR_SIZE = 28.dp

/**
 * Lista de usuarios del canal activo (UI-12): agrupada por modo (operadores, con voz y el resto), con el avatar
 * de cada nick y el nick en su color. Un toque abre el menú de acciones del usuario.
 */
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
    val groups = remember(filteredUserList) { UserListGrouping.group(filteredUserList) }

    if (activeTarget?.startsWith("#") == true) {
        if (filteredUserList.isEmpty()) {
            Text(
                text = stringResource(Res.string.no_users_in_channel),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                groups.forEach { group ->
                    item(key = "group-${group.role}") {
                        Text(
                            text = stringResource(
                                when (group.role) {
                                    UserRole.OPERATOR -> Res.string.users_group_operators
                                    UserRole.VOICED -> Res.string.users_group_voiced
                                    UserRole.REGULAR -> Res.string.users_group_regular
                                },
                                group.users.size
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(group.users, key = { it }) { userName ->
                        val nick = cleanNick(userName)
                        Box { // Needed for DropdownMenu positioning
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedUserMenu = userName } // Toda la fila es clickeable
                                    .padding(vertical = 6.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NickAvatar(nick = nick, isOwn = false, size = USER_AVATAR_SIZE)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = nick,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = nickColor(nick),
                                    modifier = Modifier.weight(1f)
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
        }
    } else {
        Text(
            text = stringResource(Res.string.select_channel_to_see_users),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
