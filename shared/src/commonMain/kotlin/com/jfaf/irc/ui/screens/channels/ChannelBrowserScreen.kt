package com.jfaf.irc.ui.screens.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jfaf.irc.domain.channel.ChannelListEntry
import com.jfaf.irc.domain.channel.ChannelListSearch
import com.jfaf.irc.domain.channel.ChannelListSort
import com.jfaf.irc.domain.channel.ChannelListStatus
import com.jfaf.irc.domain.chat.MircColorParser
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.cd_channel_has_key
import com.jfaf.irc.resources.cd_navigate_up
import com.jfaf.irc.resources.channel_browser_count
import com.jfaf.irc.resources.channel_browser_count_filtered
import com.jfaf.irc.resources.channel_browser_empty
import com.jfaf.irc.resources.channel_browser_incomplete
import com.jfaf.irc.resources.channel_browser_key_label
import com.jfaf.irc.resources.channel_browser_key_title
import com.jfaf.irc.resources.channel_browser_loading
import com.jfaf.irc.resources.channel_browser_no_matches
import com.jfaf.irc.resources.channel_browser_no_topic
import com.jfaf.irc.resources.channel_browser_not_connected
import com.jfaf.irc.resources.channel_browser_only_decoys
import com.jfaf.irc.resources.channel_browser_open
import com.jfaf.irc.resources.channel_browser_refresh
import com.jfaf.irc.resources.channel_browser_refused
import com.jfaf.irc.resources.channel_browser_retry
import com.jfaf.irc.resources.channel_browser_search_hint
import com.jfaf.irc.resources.channel_browser_sort_name
import com.jfaf.irc.resources.channel_browser_sort_users
import com.jfaf.irc.resources.channel_browser_title
import com.jfaf.irc.resources.channel_browser_users
import com.jfaf.irc.ui.common.ThemedOutlinedTextField
import com.jfaf.irc.ui.screens.chat.InputDialog
import com.jfaf.irc.ui.viewmodels.ChannelListUiState
import org.jetbrains.compose.resources.stringResource

/**
 * Explorador de canales: la lista del servidor (`LIST`) con buscador por nombre o tema, orden por
 * usuarios o por nombre y un toque para unirse. Un canal con clave (`+k`) la pide antes; uno ya
 * abierto lleva «Abierto» y el toque lleva a él.
 *
 * @param joinedChannels canales abiertos, en minúsculas.
 * @param onLoad pide la lista si hace falta (al abrir la pantalla); no repite una consulta reciente.
 * @param onRefresh pide la lista otra vez, aunque sea reciente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelBrowserScreen(
    state: ChannelListUiState,
    joinedChannels: Set<String>,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onJoin: (channel: String, key: String?) -> Unit,
    onOpenJoined: (channel: String) -> Unit,
    onNavigateUp: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ChannelListSort.BY_USERS) }
    var keyedChannel by remember { mutableStateOf<ChannelListEntry?>(null) }

    LaunchedEffect(Unit) { onLoad() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.channel_browser_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.cd_navigate_up))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = state !is ChannelListUiState.Loading) {
                        Icon(Icons.Filled.Refresh, stringResource(Res.string.channel_browser_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        // En una ventana ancha de escritorio la lista no se estira de punta a punta
        Box(Modifier.padding(paddingValues).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                ThemedOutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.channel_browser_search_hint)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sort == ChannelListSort.BY_USERS,
                        onClick = { sort = ChannelListSort.BY_USERS },
                        label = { Text(stringResource(Res.string.channel_browser_sort_users)) }
                    )
                    FilterChip(
                        selected = sort == ChannelListSort.BY_NAME,
                        onClick = { sort = ChannelListSort.BY_NAME },
                        label = { Text(stringResource(Res.string.channel_browser_sort_name)) }
                    )
                }
                when (state) {
                    is ChannelListUiState.Idle, is ChannelListUiState.Loading -> LoadingBody()
                    is ChannelListUiState.NotConnected -> MessageBody(
                        text = stringResource(Res.string.channel_browser_not_connected),
                        onRetry = onRefresh
                    )
                    is ChannelListUiState.Ready -> ReadyBody(
                        state = state,
                        query = query,
                        sort = sort,
                        joinedChannels = joinedChannels,
                        onRetry = onRefresh,
                        onChannelClick = { entry ->
                            when {
                                entry.name.lowercase() in joinedChannels -> onOpenJoined(entry.name)
                                entry.hasKey -> keyedChannel = entry
                                else -> onJoin(entry.name, null)
                            }
                        }
                    )
                }
            }
        }
    }

    keyedChannel?.let { entry ->
        InputDialog(
            title = stringResource(Res.string.channel_browser_key_title, entry.name),
            label = stringResource(Res.string.channel_browser_key_label),
            onDismiss = { keyedChannel = null },
            onConfirm = { key ->
                keyedChannel = null
                onJoin(entry.name, key.trim().ifEmpty { null })
            }
        )
    }
}

@Composable
private fun LoadingBody() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.size(16.dp))
        Text(
            stringResource(Res.string.channel_browser_loading),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun MessageBody(text: String, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = MaterialTheme.colorScheme.onBackground)
        if (onRetry != null) {
            Spacer(Modifier.size(16.dp))
            Button(onClick = onRetry) { Text(stringResource(Res.string.channel_browser_retry)) }
        }
    }
}

@Composable
private fun ReadyBody(
    state: ChannelListUiState.Ready,
    query: String,
    sort: ChannelListSort,
    joinedChannels: Set<String>,
    onRetry: () -> Unit,
    onChannelClick: (ChannelListEntry) -> Unit
) {
    val result = state.result
    if (result.status == ChannelListStatus.REFUSED && result.entries.isEmpty()) {
        MessageBody(stringResource(Res.string.channel_browser_refused), onRetry)
        return
    }
    val plainTopic = { topic: String -> MircColorParser.parse(topic).text }
    // Los canales señuelo (trampa contra bots de spam) no se pueden usar: fuera de la lista
    val entries = remember(result) { ChannelListSearch.withoutDecoys(result.entries, plainTopic) }
    val visible = remember(entries, query, sort) {
        ChannelListSearch.filterAndSort(entries, query, sort, plainTopic)
    }
    if (result.entries.isEmpty()) {
        MessageBody(stringResource(Res.string.channel_browser_empty), null)
        return
    }
    if (entries.isEmpty()) {
        // El servidor sólo ha mandado señuelos: no entrega los canales reales en esta consulta
        MessageBody(stringResource(Res.string.channel_browser_only_decoys), onRetry)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (result.status != ChannelListStatus.COMPLETE) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        stringResource(
                            if (result.status == ChannelListStatus.REFUSED) Res.string.channel_browser_refused
                            else Res.string.channel_browser_incomplete
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        item {
            Text(
                text = if (query.isBlank()) stringResource(Res.string.channel_browser_count, entries.size)
                else stringResource(Res.string.channel_browser_count_filtered, visible.size, entries.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
        if (visible.isEmpty()) {
            item {
                Text(
                    stringResource(Res.string.channel_browser_no_matches),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            items(visible, key = { it.name.lowercase() }) { entry ->
                ChannelRow(
                    entry = entry,
                    isJoined = entry.name.lowercase() in joinedChannels,
                    onClick = { onChannelClick(entry) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ChannelRow(entry: ChannelListEntry, isJoined: Boolean, onClick: () -> Unit) {
    // Los códigos de color de mIRC no se pintan en la lista: sólo el texto, en una línea de dos como mucho
    val topic = remember(entry.topic) {
        MircColorParser.parse(entry.topic).text.replace(Regex("""\s+"""), " ").trim()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // El nombre (con el candado y «Abierto») se queda con el espacio libre: el contador va siempre pegado a la derecha
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.hasKey) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(Res.string.cd_channel_has_key),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp).size(14.dp)
                    )
                }
                if (isJoined) {
                    Text(
                        text = stringResource(Res.string.channel_browser_open),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).size(14.dp)
            )
            Text(
                text = stringResource(Res.string.channel_browser_users, entry.users),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Text(
            text = topic.ifEmpty { stringResource(Res.string.channel_browser_no_topic) },
            style = MaterialTheme.typography.bodySmall,
            color = if (topic.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
