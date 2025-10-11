package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jfaf.irc.R
import com.jfaf.irc.ui.common.ThemedOutlinedTextField
import com.jfaf.irc.ui.theme.IRCTheme

@Composable
fun InputDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var textState by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = { 
            ThemedOutlinedTextField(
                value = textState, 
                onValueChange = { textState = it }, 
                label = { Text(label) }, 
                singleLine = true, 
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { 
            Button(
                onClick = { onConfirm(textState) }, 
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary, 
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) { 
                Text(stringResource(R.string.button_accept)) 
            }
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text(stringResource(R.string.button_cancel), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)) 
            }
        },
        containerColor = IRCTheme.dialogContainerOpaque
    )
}
