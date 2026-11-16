package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jfaf.irc.domain.user.WhoisResult
import com.jfaf.irc.domain.user.channelWithoutStatusPrefix
import com.jfaf.irc.domain.user.formatCompactDuration
import com.jfaf.irc.resources.*
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.ui.viewmodels.WhoisUiState
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Resultado de un WHOIS: mientras el servidor responde muestra una espera y después los datos
 * agrupados. Los textos se pueden seleccionar y copiar y los canales (`#…`) se pulsan para unirse.
 */
@Composable
fun WhoisDialog(
    state: WhoisUiState,
    onJoinChannel: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Vuelve a pedir el WHOIS de [nick] (sólo se ofrece cuando el servidor no respondió). */
    onRetry: (nick: String) -> Unit = {}
) {
    val nick = when (state) {
        is WhoisUiState.Loading -> state.nick
        is WhoisUiState.Result -> state.result.nick
        is WhoisUiState.TimedOut -> state.nick
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.whois_dialog_title, nick)) },
        text = {
            when (state) {
                is WhoisUiState.Loading -> LoadingBody(nick)
                is WhoisUiState.TimedOut -> Text(stringResource(Res.string.whois_dialog_timeout, nick))
                is WhoisUiState.Result ->
                    if (state.result.found) {
                        ResultBody(state.result, onJoinChannel = { onJoinChannel(it); onDismiss() })
                    } else {
                        Text(stringResource(Res.string.whois_dialog_not_found, nick))
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.whois_dialog_close)) }
        },
        dismissButton = if (state is WhoisUiState.TimedOut) {
            { TextButton(onClick = { onRetry(nick) }) { Text(stringResource(Res.string.whois_dialog_retry)) } }
        } else null,
        containerColor = IRCTheme.dialogContainerOpaque
    )
}

@Composable
private fun LoadingBody(nick: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(Res.string.whois_dialog_loading, nick))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultBody(result: WhoisResult, onJoinChannel: (String) -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val identity = listOfNotNull(result.user, result.host).joinToString("@")
                Field(stringResource(Res.string.whois_label_identity), identity.ifBlank { null })
                Field(stringResource(Res.string.whois_label_real_name), result.realName)
                Field(
                    stringResource(Res.string.whois_label_server),
                    result.server?.let { server -> result.serverInfo?.takeIf { it.isNotBlank() }?.let { "$server ($it)" } ?: server }
                )
                Field(stringResource(Res.string.whois_label_real_host), result.actualHost)
                Field(stringResource(Res.string.whois_label_account), result.account)
                Field(stringResource(Res.string.whois_label_away), result.awayMessage)
                Field(stringResource(Res.string.whois_label_idle), result.idleSeconds?.let(::formatCompactDuration))
                Field(
                    stringResource(Res.string.whois_label_connected),
                    result.signOnEpochSeconds?.let { formatCompactDuration(Clock.System.now().epochSeconds - it) }
                )
                if (result.isOperator) Text(stringResource(Res.string.whois_flag_operator), style = MaterialTheme.typography.bodyMedium)
                if (result.isSecure) Text(stringResource(Res.string.whois_flag_secure), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (result.channels.isNotEmpty()) {
            Text(
                stringResource(Res.string.whois_label_channels),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                result.channels.forEach { raw ->
                    val channel = raw.channelWithoutStatusPrefix()
                    AssistChip(
                        onClick = { onJoinChannel(channel) },
                        label = { Text(raw) },
                        enabled = channel.startsWith("#")
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
