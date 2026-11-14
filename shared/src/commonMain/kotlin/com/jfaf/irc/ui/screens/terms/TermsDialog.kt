package com.jfaf.irc.ui.screens.terms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.jfaf.irc.domain.terms.TermsOfUse
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.terms_accept
import com.jfaf.irc.resources.terms_close
import com.jfaf.irc.resources.terms_decline
import com.jfaf.irc.resources.terms_intro
import com.jfaf.irc.resources.terms_text
import com.jfaf.irc.resources.terms_title
import org.jetbrains.compose.resources.stringResource

/**
 * Condiciones de uso. Con [onAccept] es el diálogo obligatorio del primer arranque: no se cierra
 * tocando fuera ni con «atrás», sólo aceptando o saliendo ([onDecline]). Sin [onAccept] es la
 * consulta desde Ajustes y [onDecline] sólo lo cierra.
 */
@Composable
fun TermsDialog(onDecline: () -> Unit, onAccept: (() -> Unit)? = null) {
    val mustAccept = onAccept != null
    AlertDialog(
        onDismissRequest = { if (!mustAccept) onDecline() },
        properties = DialogProperties(dismissOnBackPress = !mustAccept, dismissOnClickOutside = !mustAccept),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(Res.string.terms_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (mustAccept) {
                    Text(stringResource(Res.string.terms_intro), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                Text(stringResource(Res.string.terms_text, TermsOfUse.MINIMUM_AGE), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (onAccept != null) {
                TextButton(onClick = onAccept, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(stringResource(Res.string.terms_accept)) }
            } else {
                TextButton(onClick = onDecline, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(stringResource(Res.string.terms_close)) }
            }
        },
        dismissButton = if (mustAccept) {
            {
                TextButton(
                    onClick = onDecline,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) { Text(stringResource(Res.string.terms_decline)) }
            }
        } else null
    )
}
