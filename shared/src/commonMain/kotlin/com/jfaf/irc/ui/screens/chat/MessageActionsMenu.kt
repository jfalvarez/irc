package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.jfaf.irc.domain.chat.MessageActions
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.action_copy_message
import com.jfaf.irc.resources.action_mention_user
import com.jfaf.irc.resources.action_quote_message
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.ui.viewmodels.MainViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Menú que se abre al mantener pulsado un mensaje: copiar, mencionar a quien lo escribió y citarlo.
 * Qué ofrece lo decide [MessageActions]. Debe colocarse dentro del mismo Box que el elemento al que se ancla.
 */
@Composable
fun MessageActionsMenu(
    message: UiChatMessage,
    expanded: Boolean,
    onDismiss: () -> Unit,
    mainViewModel: MainViewModel
) {
    @Suppress("DEPRECATION") // el LocalClipboard nuevo exige un ClipEntry propio de cada plataforma
    val clipboard = LocalClipboardManager.current
    val mention = MessageActions.mentionInsertion(message)
    val quote = MessageActions.quoteInsertion(message)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(IRCTheme.dropdownMenuContainerOpaque)
    ) {
        if (MessageActions.canCopy(message)) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_copy_message), color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    val text = MessageActions.copyText(message)
                    clipboard.setText(AnnotatedString(text))
                    mainViewModel.onMessageCopied()
                    onDismiss()
                }
            )
        }
        if (mention != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_mention_user), color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    mainViewModel.requestInputInsertion(mention)
                    onDismiss()
                }
            )
        }
        if (quote != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_quote_message), color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    mainViewModel.requestInputInsertion(quote)
                    onDismiss()
                }
            )
        }
    }
}
