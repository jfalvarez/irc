package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jfaf.irc.R
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.ui.viewmodels.MainViewModel

/** Quita el prefijo de modo de canal (@, +, %, &, ~) de un nick. */
fun cleanNick(nick: String): String =
    if (nick.isNotEmpty() && nick[0] in "@+%&~") nick.substring(1) else nick

/**
 * Menú contextual de acciones sobre un usuario (mensaje privado, ignorar, whois).
 * Compartido por la lista de usuarios del canal y por el nick de los mensajes.
 * Debe colocarse dentro del mismo Box que el elemento al que se ancla.
 */
@Composable
fun UserActionsMenu(
    userName: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
    mainViewModel: MainViewModel
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(IRCTheme.dropdownMenuContainerOpaque)
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(R.string.action_send_private_message),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            },
            onClick = {
                mainViewModel.openPrivateMessage(cleanNick(userName))
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(R.string.action_ignore_user),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            },
            onClick = {
                mainViewModel.ignoreUser(cleanNick(userName))
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(R.string.action_whois),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            },
            onClick = {
                mainViewModel.performWhois(cleanNick(userName))
                onDismiss()
            }
        )
    }
}
