package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onJoinChannelRequest: () -> Unit, // This might need review if the dialogs are not accessible globally
    onOpenPrivateMessageRequest: () -> Unit, // Same as above
    onCloseTargetAction: (String) -> Unit, 
    onSettingsClick: () -> Unit
) {
    val serverString = stringResource(R.string.cd_server)
    ModalDrawerSheet(drawerContainerColor = IRCTheme.drawerContainerOpaque) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                stringResource(R.string.drawer_title_channels_chats), 
                modifier = Modifier.padding(16.dp), 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.drawer_user_connected_as, currentNick), 
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), 
                style = MaterialTheme.typography.bodySmall, 
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
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
                        label = { 
                            Text(
                                if (isServerTarget) serverString else target, 
                                fontWeight = fontWeight, 
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        },
                        selected = isSelected, 
                        onClick = { onTargetSelected(target) },
                        badge = {
                            if (!isServerTarget && target != activeTarget) {
                                IconButton(onClick = { onCloseTargetAction(target) }) { 
                                    Icon(
                                        Icons.AutoMirrored.Filled.ExitToApp, 
                                        stringResource(R.string.cd_close_target, target), 
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), 
                            unselectedContainerColor = Color.Transparent, 
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary, 
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), 
                            selectedTextColor = MaterialTheme.colorScheme.onPrimary, 
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        ),
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            val settingsSelected = false // This state seems local and unused for selection color, kept for consistency for now
            NavigationDrawerItem(
                icon = { 
                    Icon(
                        Icons.Filled.Settings, 
                        stringResource(R.string.menu_item_settings), 
                        tint = if (settingsSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                },
                label = { 
                    Text(
                        stringResource(R.string.menu_item_settings), 
                        color = if (settingsSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                },
                selected = settingsSelected, 
                onClick = onSettingsClick,
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), 
                    unselectedContainerColor = Color.Transparent, 
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary, 
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), 
                    selectedTextColor = MaterialTheme.colorScheme.onPrimary, 
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                ),
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}
