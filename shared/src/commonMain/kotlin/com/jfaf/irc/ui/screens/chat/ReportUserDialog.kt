package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.report_ignore
import com.jfaf.irc.resources.report_text
import com.jfaf.irc.resources.report_title
import com.jfaf.irc.resources.terms_close
import org.jetbrains.compose.resources.stringResource

/**
 * «Denunciar a [nick]». La app no aloja ni modera los canales (son de la red IRC): explica a quién
 * avisar (operadores del canal o de la red, y autoridades si puede ser delito) y ofrece ignorarlo.
 */
@Composable
fun ReportUserDialog(nick: String, onIgnore: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(Res.string.report_title, nick)) },
        text = {
            Text(
                stringResource(Res.string.report_text, nick),
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = { onIgnore(); onDismiss() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(stringResource(Res.string.report_ignore, nick)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(stringResource(Res.string.terms_close)) }
        }
    )
}
