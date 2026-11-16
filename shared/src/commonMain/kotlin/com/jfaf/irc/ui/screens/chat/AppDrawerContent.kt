package com.jfaf.irc.ui.screens.chat

import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.PermanentDrawerSheet
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jfaf.irc.ui.theme.IRCTheme

private const val MAX_BADGE_COUNT = 99

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title, // Consider more specific CD for screen readers if title isn't enough
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.padding(start = 12.dp, end = 12.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (isExpanded) stringResource(Res.string.cd_collapse) else stringResource(Res.string.cd_expand),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}

@Composable
private fun DrawerListItem(
    target: String,
    isSelected: Boolean,
    isUnread: Boolean,
    onTargetSelected: (String) -> Unit,
    onCloseTargetAction: ((String) -> Unit)?,
    trailingIcon: @Composable (() -> Unit)? = null,
    /** Privados sin leer de esta conversación; 0 = sin insignia. */
    unreadCount: Int = 0,
    /** El contador son menciones de tu nick (canales) y no privados. */
    countsMentions: Boolean = false,
    /** Canal silenciado (UI-32): se marca con una campana tachada. */
    muted: Boolean = false
) {
    val fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
    val textColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)

    NavigationDrawerItem(
        label = { Text(target, fontWeight = fontWeight, color = textColor) },
        selected = isSelected,
        onClick = { onTargetSelected(target) },
        badge = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (muted) {
                    Icon(
                        Icons.Outlined.NotificationsOff,
                        stringResource(Res.string.cd_channel_muted),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(end = 6.dp).size(16.dp)
                    )
                }
                if (unreadCount > 0) {
                    val description = stringResource(
                        if (countsMentions) Res.string.cd_unread_mentions else Res.string.cd_unread_messages,
                        unreadCount
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.semantics { contentDescription = description }
                    ) {
                        Text(if (unreadCount > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else unreadCount.toString())
                    }
                }
                if (onCloseTargetAction != null) {
                    IconButton(onClick = { onCloseTargetAction(target) }) {
                        Icon(
                            Icons.Filled.Close,
                            stringResource(Res.string.cd_close_target, target),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        },
        icon = trailingIcon,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            unselectedContainerColor = Color.Transparent,
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(start = 16.dp)
    )
}

@Composable
fun AppDrawerContent(
    chatTargets: List<String>,
    activeTarget: String?,
    unreadTargets: Set<String>,
    unreadPmCounts: Map<String, Int>,
    /** Menciones de tu nick sin leer por canal. */
    unreadMentionCounts: Map<String, Int> = emptyMap(),
    onlineFriends: Set<String>,
    currentNick: String,
    onTargetSelected: (String) -> Unit,
    onCloseTargetAction: (String) -> Unit,
    onOpenPrivateMessage: (String) -> Unit,
    onSettingsClick: () -> Unit,
    /** En la ventana ancha de escritorio el panel es fijo en vez de un cajón modal. */
    permanent: Boolean = false,
    /** Hay enlace con el servidor; si no, la lista de pestañas se ve apagada. */
    linkUp: Boolean = true,
    /** Canales silenciados (nombres en minúsculas): llevan una campana tachada (UI-32). */
    mutedChannels: Set<String> = emptySet(),
    /** Abre el selector rápido (UI-30); sin él no se muestra la fila de búsqueda. */
    onQuickSwitch: (() -> Unit)? = null,
    /** Abre el explorador de canales; sin él no se muestra la fila. */
    onBrowseChannels: (() -> Unit)? = null
) {
    val serverString = stringResource(Res.string.cd_server)
    val distinctTargets = chatTargets.distinct()

    val serverTargetItem = distinctTargets.find { it == serverString }
    val channelItems = distinctTargets.filter { it.startsWith("#") }
    val conversationItems = distinctTargets.filter { !it.startsWith("#") && it != serverString }
    val friendsToShow = onlineFriends.filter { friend -> conversationItems.none { it.equals(friend, ignoreCase = true) } }

    var channelsExpanded by remember { mutableStateOf(true) }
    var conversationsExpanded by remember { mutableStateOf(true) }
    var friendsExpanded by remember { mutableStateOf(true) }

    val sheetContent: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                stringResource(Res.string.drawer_title_channels_chats),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(Res.string.drawer_user_connected_as, currentNick),
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            onQuickSwitch?.let { DrawerSearchRow(onClick = it) }
            onBrowseChannels?.let { DrawerBrowseChannelsRow(onClick = it) }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            LazyColumn(modifier = Modifier.weight(1f).alpha(if (linkUp) 1f else 0.5f)) {
                serverTargetItem?.let { target ->
                    item {
                        val isSelected = target == activeTarget
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Outlined.Dns, stringResource(Res.string.cd_server), tint = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)) },
                            label = {
                                Text(
                                    target,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                            },
                            selected = isSelected,
                            onClick = { onTargetSelected(target) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                unselectedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier
                        )
                    }
                }

                if (friendsToShow.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(Res.string.drawer_section_online_friends),
                            icon = Icons.Filled.People,
                            isExpanded = friendsExpanded,
                            onToggleExpand = { friendsExpanded = !friendsExpanded }
                        )
                    }
                    item {
                        AnimatedVisibility(visible = friendsExpanded) {
                            Column {
                                friendsToShow.forEach { friend ->
                                    DrawerListItem(
                                        target = friend,
                                        isSelected = friend == activeTarget,
                                        isUnread = unreadTargets.contains(friend),
                                        onTargetSelected = { onOpenPrivateMessage(friend) },
                                        onCloseTargetAction = null,
                                        trailingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                        unreadCount = unreadPmCounts[friend] ?: 0
                                    )
                                }
                            }
                        }
                    }
                }

                if (channelItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(Res.string.drawer_section_channels),
                            icon = Icons.Outlined.Forum,
                            isExpanded = channelsExpanded,
                            onToggleExpand = { channelsExpanded = !channelsExpanded }
                        )
                    }
                    item {
                        AnimatedVisibility(visible = channelsExpanded) {
                            Column {
                                channelItems.forEach { target ->
                                    // Un canal silenciado no avisa de nada: ni en negrita por sin leer ni con insignia de menciones
                                    val muted = target.lowercase() in mutedChannels
                                    DrawerListItem(
                                        target = target,
                                        isSelected = target == activeTarget,
                                        isUnread = !muted && unreadTargets.contains(target),
                                        onTargetSelected = onTargetSelected,
                                        onCloseTargetAction = onCloseTargetAction,
                                        unreadCount = if (muted) 0 else unreadMentionCounts[target] ?: 0,
                                        countsMentions = true,
                                        muted = muted
                                    )
                                }
                            }
                        }
                    }
                }

                if (conversationItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(Res.string.drawer_section_conversations),
                            icon = Icons.Outlined.Person,
                            isExpanded = conversationsExpanded,
                            onToggleExpand = { conversationsExpanded = !conversationsExpanded }
                        )
                    }
                    item {
                        AnimatedVisibility(visible = conversationsExpanded) {
                            Column {
                                conversationItems.forEach { target ->
                                    DrawerListItem(
                                        target = target,
                                        isSelected = target == activeTarget,
                                        isUnread = unreadTargets.contains(target),
                                        onTargetSelected = onTargetSelected,
                                        onCloseTargetAction = onCloseTargetAction,
                                        unreadCount = unreadPmCounts[target] ?: 0
                                    )
                                }
                            }
                        }
                    }
                }
            } // Fin LazyColumn

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            NavigationDrawerItem(
                icon = {
                    Icon(
                        Icons.Filled.Settings,
                        stringResource(Res.string.menu_item_settings),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                },
                label = {
                    Text(
                        stringResource(Res.string.menu_item_settings),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                },
                selected = false,
                onClick = onSettingsClick,
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    unselectedContainerColor = Color.Transparent,
                ),
                modifier = Modifier
            )
        }
    }

    if (permanent) {
        PermanentDrawerSheet(
            modifier = Modifier.width(300.dp),
            drawerContainerColor = IRCTheme.drawerContainerOpaque,
            content = sheetContent
        )
    } else {
        ModalDrawerSheet(drawerContainerColor = IRCTheme.drawerContainerOpaque, content = sheetContent)
    }
}
