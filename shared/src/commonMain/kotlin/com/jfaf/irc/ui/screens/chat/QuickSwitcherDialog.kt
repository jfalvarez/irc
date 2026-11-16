package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jfaf.irc.domain.chat.ChatStateManager
import com.jfaf.irc.domain.chat.QuickSwitcher
import com.jfaf.irc.domain.notifications.ChannelNotificationLevels
import com.jfaf.irc.domain.chat.QuickSwitcher.Entry
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.quick_switcher_empty
import com.jfaf.irc.resources.quick_switcher_friend
import com.jfaf.irc.resources.quick_switcher_hint
import com.jfaf.irc.resources.quick_switcher_join
import com.jfaf.irc.resources.quick_switcher_open_private
import com.jfaf.irc.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Selector rápido (Ctrl+K, UI-30): un cuadro de búsqueda para saltar a un canal o conversación sin recorrer las
 * pestañas. Flechas arriba y abajo mueven la selección, Intro la elige, Esc o Ctrl+K lo cierran. Qué sale lo decide
 * [QuickSwitcher].
 */
@Composable
fun QuickSwitcherDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val state = viewModel.chatScreenState
    val targets by state.chatTargets.collectAsState()
    val active by state.activeTarget.collectAsState()
    val unread by state.unreadTargets.collectAsState()
    val mentions by state.unreadMentionCounts.collectAsState()
    val pms by state.unreadPmCounts.collectAsState()
    val friends by state.onlineFriends.collectAsState()
    val levels by viewModel.channelNotificationLevels.collectAsState()
    val defaultLevel by viewModel.defaultChannelNotificationLevel.collectAsState()
    val muted = remember(targets, levels, defaultLevel) { ChannelNotificationLevels.mutedAmong(targets, levels, defaultLevel) }

    var query by remember { mutableStateOf("") }
    val entries = remember(targets, active, unread, mentions, pms, friends, query, muted) {
        QuickSwitcher.search(targets, active, unread, mentions, pms, friends, query, muted)
    }
    var selected by remember(query) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    fun choose(entry: Entry) {
        when (entry) {
            is Entry.Tab -> viewModel.setActiveTarget(entry.target)
            is Entry.OpenPrivate -> viewModel.openPrivateMessage(entry.nick)
            is Entry.JoinChannel -> viewModel.joinChannel(entry.channel)
        }
        onDismiss()
    }

    // El campo aún puede no estar colocado en el primer instante: se reintenta hasta que acepta el foco
    LaunchedEffect(Unit) {
        withFrameNanos { }
        repeat(20) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(30)
        }
    }
    LaunchedEffect(selected, entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(selected.coerceIn(0, entries.lastIndex))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
        ) {
            Column {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(Res.string.quick_switcher_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { entries.getOrNull(selected)?.let(::choose) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                event.key == Key.DirectionDown -> {
                                    if (entries.isNotEmpty()) selected = (selected + 1).coerceAtMost(entries.lastIndex)
                                    true
                                }
                                event.key == Key.DirectionUp -> {
                                    selected = (selected - 1).coerceAtLeast(0)
                                    true
                                }
                                event.isCtrlPressed && event.key == Key.K -> {
                                    onDismiss()
                                    true
                                }
                                else -> false
                            }
                        }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (entries.isEmpty()) {
                    Text(
                        stringResource(Res.string.quick_switcher_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.heightIn(max = 360.dp)) {
                        items(entries.size) { index ->
                            QuickSwitcherRow(entry = entries[index], selected = index == selected, onClick = { choose(entries[index]) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSwitcherRow(entry: Entry, selected: Boolean, onClick: () -> Unit) {
    val title = when (entry) {
        is Entry.Tab -> entry.target
        is Entry.OpenPrivate -> entry.nick
        is Entry.JoinChannel -> entry.channel
    }
    val subtitle = when (entry) {
        is Entry.Tab -> null
        is Entry.OpenPrivate -> stringResource(if (entry.isFriend) Res.string.quick_switcher_friend else Res.string.quick_switcher_open_private)
        is Entry.JoinChannel -> stringResource(Res.string.quick_switcher_join)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            entry is Entry.JoinChannel -> SwitcherIcon { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
            entry is Entry.Tab && entry.target == ChatStateManager.SERVER_TARGET_ID ->
                SwitcherIcon { Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
            entry is Entry.Tab && entry.target.startsWith("#") ->
                SwitcherIcon { Text("#", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium) }
            else -> NickAvatar(nick = title, isOwn = false, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (entry is Entry.Tab) {
            val count = if (entry.mentions > 0) entry.mentions else entry.unreadMessages
            if (count > 0) {
                Box(
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            } else if (entry.unread) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun SwitcherIcon(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) { content() }
}
