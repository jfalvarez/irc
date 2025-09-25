package com.jfaf.irc.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Forum // Icono para Canales
import androidx.compose.material.icons.outlined.Person // Icono para Conversaciones
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jfaf.irc.R
import com.jfaf.irc.ui.theme.IRCTheme

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
    onSettingsClick: () -> Unit
) {
    val serverString = stringResource(R.string.cd_server)
    val distinctTargets = chatTargets.distinct()

    val serverTargetItem = distinctTargets.find { it == serverString }
    val channelItems = distinctTargets.filter { it.startsWith("#") }
    val conversationItems = distinctTargets.filter { !it.startsWith("#") && it != serverString }

    var channelsExpanded by remember { mutableStateOf(true) }
    var conversationsExpanded by remember { mutableStateOf(true) }

    ModalDrawerSheet(drawerContainerColor = IRCTheme.drawerContainerOpaque) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) { // Added padding to avoid settings sticking to bottom edge
            Text(
                stringResource(R.string.drawer_title_channels_chats),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.drawer_user_connected_as, currentNick),
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            LazyColumn(modifier = Modifier.weight(1f)) {
                // Server Target
                serverTargetItem?.let { target ->
                    item {
                        val isSelected = target == activeTarget
                        val iconColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Menu, stringResource(R.string.cd_server), tint = iconColor) },
                            label = {
                                Text(
                                    target,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                            },
                            selected = isSelected,
                            onClick = { onTargetSelected(target) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                unselectedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier // Removed NavigationDrawerItemDefaults.ItemPadding
                        )
                    }
                }

                // Channels Section Header
                if (channelItems.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { channelsExpanded = !channelsExpanded }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Forum,
                                contentDescription = stringResource(R.string.drawer_section_channels),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp)
                            )
                            Text(
                                stringResource(R.string.drawer_section_channels),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (channelsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (channelsExpanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                    }
                    // Channel Items (conditionally visible)
                    item { // Wrap AnimatedVisibility in an item
                        AnimatedVisibility(visible = channelsExpanded && channelItems.isNotEmpty()) {
                            Column {
                                channelItems.forEach { target ->
                                    val isUnread = unreadTargets.contains(target)
                                    val fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                                    val isSelected = target == activeTarget
                                    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                    NavigationDrawerItem(
                                        label = { Text(target, fontWeight = fontWeight, color = textColor) },
                                        selected = isSelected,
                                        onClick = { onTargetSelected(target) },
                                        badge = {
                                            IconButton(onClick = { onCloseTargetAction(target) }) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ExitToApp,
                                                    stringResource(R.string.cd_close_target, target),
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            unselectedContainerColor = Color.Transparent,
                                        ),
                                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(start = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Conversations Section Header
                if (conversationItems.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { conversationsExpanded = !conversationsExpanded }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = stringResource(R.string.drawer_section_conversations),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp)
                            )
                            Text(
                                stringResource(R.string.drawer_section_conversations),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (conversationsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (conversationsExpanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                 modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                    }
                    // Conversation Items (conditionally visible)
                    item { // Wrap AnimatedVisibility in an item
                        AnimatedVisibility(visible = conversationsExpanded && conversationItems.isNotEmpty()) {
                            Column {
                                conversationItems.forEach { target ->
                                    val isUnread = unreadTargets.contains(target)
                                    val fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                                    val isSelected = target == activeTarget
                                    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                    NavigationDrawerItem(
                                        label = { Text(target, fontWeight = fontWeight, color = textColor) },
                                        selected = isSelected,
                                        onClick = { onTargetSelected(target) },
                                        badge = {
                                            IconButton(onClick = { onCloseTargetAction(target) }) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ExitToApp,
                                                    stringResource(R.string.cd_close_target, target),
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            unselectedContainerColor = Color.Transparent,
                                        ),
                                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(start = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } // Fin LazyColumn

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            // Settings Item
            NavigationDrawerItem(
                icon = {
                    Icon(
                        Icons.Filled.Settings,
                        stringResource(R.string.menu_item_settings),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) 
                    )
                },
                label = {
                    Text(
                        stringResource(R.string.menu_item_settings),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                },
                selected = false, 
                onClick = onSettingsClick,
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    unselectedContainerColor = Color.Transparent,
                ),
                modifier = Modifier // Removed NavigationDrawerItemDefaults.ItemPadding
            )
        }
    }
}
