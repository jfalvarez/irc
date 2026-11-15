package com.jfaf.irc.ui.screens.connection

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jfaf.irc.domain.usecase.DeleteAccountResult
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.account_delete_account_failed
import com.jfaf.irc.resources.account_delete_confirm_button
import com.jfaf.irc.resources.account_delete_confirm_text
import com.jfaf.irc.resources.account_delete_confirm_title
import com.jfaf.irc.resources.account_delete_data_failed
import com.jfaf.irc.resources.account_delete_done
import com.jfaf.irc.resources.account_delete_not_signed_in
import com.jfaf.irc.resources.account_delete_reauth_failed
import com.jfaf.irc.resources.account_delete_wrong_account
import com.jfaf.irc.resources.button_accept
import com.jfaf.irc.resources.button_cancel
import com.jfaf.irc.ui.viewmodels.SignInViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Confirmación de «Eliminar cuenta»; se muestra al elegirlo en el menú de la cuenta. */
@Composable
fun DeleteAccountConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(Res.string.account_delete_confirm_title)) },
        text = { Text(stringResource(Res.string.account_delete_confirm_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(Res.string.account_delete_confirm_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(stringResource(Res.string.button_cancel)) }
        }
    )
}

/** Aviso con el resultado de borrar la cuenta. Va fuera de la parte «con sesión»: tras borrarla ya no la hay. */
@Composable
fun AccountDeletionResultDialog(signInViewModel: SignInViewModel) {
    val result by signInViewModel.deletionResult.collectAsState()
    val message = result?.let { messageFor(it) } ?: return
    AlertDialog(
        onDismissRequest = signInViewModel::dismissDeletionResult,
        containerColor = MaterialTheme.colorScheme.surface,
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = signInViewModel::dismissDeletionResult, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(stringResource(Res.string.button_accept)) }
        }
    )
}

private fun messageFor(result: DeleteAccountResult): StringResource = when (result) {
    DeleteAccountResult.DELETED -> Res.string.account_delete_done
    DeleteAccountResult.NOT_SIGNED_IN -> Res.string.account_delete_not_signed_in
    DeleteAccountResult.WRONG_ACCOUNT -> Res.string.account_delete_wrong_account
    DeleteAccountResult.REAUTH_FAILED -> Res.string.account_delete_reauth_failed
    DeleteAccountResult.DATA_NOT_DELETED -> Res.string.account_delete_data_failed
    DeleteAccountResult.ACCOUNT_NOT_DELETED -> Res.string.account_delete_account_failed
}
