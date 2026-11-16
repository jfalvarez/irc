package com.jfaf.irc.ui.screens.chat

import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import com.jfaf.irc.domain.chat.MircColorParser
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Icon
import com.jfaf.irc.ui.platform.LocalPlatformUi
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource

import com.jfaf.irc.ui.screens.chat.InputDialog
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.domain.notifications.ChannelNotificationLevel
import com.jfaf.irc.ui.theme.readableOn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopAppBar(
    activeTarget: String?,
    /** Privados sin leer en otras pestañas: se muestra un punto sobre el icono del menú. */
    unreadPmCount: Int = 0,
    onNavigationIconClick: () -> Unit,
    /** Ventana ancha de escritorio: las pestañas son un panel fijo y no hace falta el botón del cajón. */
    showNavigationIcon: Boolean = true,
    /** Ventana ancha de escritorio: la lista de usuarios es fija y no hay botón para ocultarla. */
    showUserListOption: Boolean = true,
    onDisconnectClick: () -> Unit,
    onJoinChannelRequest: (String) -> Unit,
    onOpenPrivateMessageRequest: (String) -> Unit,
    onToggleUserList: () -> Unit,
    onIgnoreUserInPm: (nick: String) -> Unit,
    onAddFriendInPm: (nick: String) -> Unit,
    onWhoisClick: (nick: String) -> Unit,
    /** Abre el explorador de canales. */
    onBrowseChannelsClick: () -> Unit = {},
    /** Abre la búsqueda dentro del chat (UI-35). */
    onSearchClick: () -> Unit = {},
    /** Usuarios del canal activo; null si no se sabe (todavía no llegó la lista). */
    userCount: Int? = null,
    /** Tema del canal activo; null o vacío = sin tema. */
    topic: String? = null,
    /** Abre el selector rápido de canales y conversaciones (UI-30). */
    onQuickSwitch: () -> Unit = {},
    /** Nivel de aviso propio del canal activo (null: sigue el predeterminado), el predeterminado y cómo cambiarlo (UI-32, UI-34); sólo se ofrece en un canal. */
    channelNotificationOverride: ChannelNotificationLevel? = null,
    defaultChannelNotificationLevel: ChannelNotificationLevel = ChannelNotificationLevel.Default,
    onChannelNotificationLevelChange: (ChannelNotificationLevel?) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showJoinChannelDialog by remember { mutableStateOf(false) }
    var showOpenPmDialog by remember { mutableStateOf(false) }
    var reportingNick by remember { mutableStateOf<String?>(null) }
    var showTopic by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }

    val isChannel = activeTarget?.startsWith("#") == true
    // Exclude SERVER_TARGET_ID (assumed to be "Servidor") from being a PM for the ignore option
    val isPm = activeTarget != null &&
               activeTarget.isNotBlank() &&
               !activeTarget.startsWith("#") &&
               activeTarget != "Servidor"

    TopAppBar(
        title = {
            val usersText = if (isChannel && userCount != null) {
                if (userCount == 1) stringResource(Res.string.chat_users_one, userCount)
                else stringResource(Res.string.chat_users_many, userCount)
            } else {
                null
            }
            // Los códigos de color de mIRC no se pintan en la cabecera: sólo el texto, en una línea
            val topicLine = topic?.let { MircColorParser.parse(it).text.replace(Regex("""\s+"""), " ").trim() }?.takeIf { it.isNotEmpty() }
            Column(modifier = if (isChannel) Modifier.clickable { showTopic = true } else Modifier) {
                Text(
                    text = activeTarget ?: stringResource(Res.string.app_title_default),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (usersText != null) {
                    Text(
                        text = if (topicLine != null) stringResource(Res.string.chat_header_with_topic, usersText, topicLine) else usersText,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (showNavigationIcon) {
                IconButton(onClick = onNavigationIconClick) {
                    BadgedBox(
                        badge = {
                            if (unreadPmCount > 0) {
                                val description = stringResource(Res.string.cd_unread_private_messages)
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.semantics { contentDescription = description }
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Menu, stringResource(Res.string.cd_open_navigation_menu))
                    }
                }
            }
        },
        actions = {
            QuickSwitchButton(onClick = onQuickSwitch)
            if (isChannel && showUserListOption) {
                IconButton(onClick = onToggleUserList) { Icon(Icons.Filled.Person, stringResource(Res.string.cd_toggle_user_list)) }
            }
            IconButton(onClick = { showMenu = !showMenu }) { Icon(Icons.Filled.MoreVert, stringResource(Res.string.cd_more_options)) }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(IRCTheme.dropdownMenuContainerOpaque)
            ) {
                if (isPm && activeTarget != null) { // activeTarget null check for safety
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_whois_user, activeTarget), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showMenu = false
                            onWhoisClick(activeTarget)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.menu_item_add_friend, activeTarget), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showMenu = false
                            onAddFriendInPm(activeTarget)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.menu_item_ignore_user, activeTarget), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showMenu = false
                            onIgnoreUserInPm(activeTarget)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.menu_item_report_user, activeTarget), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showMenu = false
                            reportingNick = activeTarget
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.menu_item_private_message_to), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showMenu = false
                            showOpenPmDialog = true
                        }
                    )
                }
                if (isChannel && activeTarget != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(Res.string.menu_item_channel_notifications, stringResource(channelNotificationLabel(channelNotificationOverride ?: defaultChannelNotificationLevel))),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showMenu = false
                            showNotifications = true
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.menu_item_search_chat), color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showMenu = false
                        onSearchClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.menu_item_browse_channels), color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showMenu = false
                        onBrowseChannelsClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.menu_item_join_channel), color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showMenu = false
                        showJoinChannelDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.menu_item_disconnect), color = MaterialTheme.colorScheme.onSurface) },
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

    if (showTopic && isChannel && activeTarget != null) {
        TopicDialog(channel = activeTarget, topic = topic, onDismiss = { showTopic = false })
    }
    if (showNotifications && isChannel && activeTarget != null) {
        ChannelNotificationsDialog(
            channel = activeTarget,
            override = channelNotificationOverride,
            defaultLevel = defaultChannelNotificationLevel,
            onSelect = {
                onChannelNotificationLevelChange(it)
                showNotifications = false
            },
            onDismiss = { showNotifications = false }
        )
    }

    if (showJoinChannelDialog) {
        InputDialog(
            title = stringResource(Res.string.dialog_title_join_channel),
            label = stringResource(Res.string.dialog_label_channel_name),
            onDismiss = { showJoinChannelDialog = false },
            onConfirm = { channelName ->
                if (channelName.isNotBlank()) {
                    onJoinChannelRequest(if (channelName.startsWith("#")) channelName else "#$channelName")
                }
                showJoinChannelDialog = false
            }
        )
    }

    reportingNick?.let { nick ->
        ReportUserDialog(nick, onIgnore = { onIgnoreUserInPm(nick) }, onDismiss = { reportingNick = null })
    }

    if (showOpenPmDialog) {
        InputDialog(
            title = stringResource(Res.string.dialog_title_open_private_message),
            label = stringResource(Res.string.dialog_label_user_nickname),
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

/** El tema del canal completo (se abre al pulsar la cabecera), con su formato mIRC. */
@Composable
private fun TopicDialog(channel: String, topic: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.topic_dialog_title, channel)) },
        text = {
            if (topic.isNullOrBlank()) {
                Text(stringResource(Res.string.topic_dialog_empty))
            } else {
                Text(MircColorParser.parse(topic).readableOn(MaterialTheme.colorScheme.surface))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.topic_dialog_close)) }
        },
        containerColor = IRCTheme.dialogContainerOpaque
    )
}

/**
 * Lupa que abre el selector rápido (UI-30). En escritorio, la ayuda emergente enseña el atajo (Ctrl+K), que es la forma
 * de descubrirlo; en el móvil no hay teclado, así que el botón es el único camino.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickSwitchButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.action_quick_switch)
    val tooltip = if (LocalPlatformUi.current.isDesktop) {
        stringResource(Res.string.action_quick_switch_with_shortcut, QUICK_SWITCH_SHORTCUT)
    } else {
        label
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick) { Icon(Icons.Filled.Search, label) }
    }
}

/** El atajo del selector rápido, tal como se muestra al usuario (`chatShortcuts` lo atiende con Ctrl). */
const val QUICK_SWITCH_SHORTCUT = "Ctrl+K"
