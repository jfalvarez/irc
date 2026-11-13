package com.jfaf.irc.ui.screens.connection

import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jfaf.irc.ui.platform.LocalPlatformUi
import com.jfaf.irc.ui.common.ThemedOutlinedTextField
import com.jfaf.irc.ui.viewmodels.SignInViewModel

@Composable
fun ConnectionSetupSection(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    nickServPasswordState: MutableState<String>,
    rememberNickServPasswordState: MutableState<Boolean>,
    signInViewModel: SignInViewModel,
    onGoogleSignIn: () -> Unit,
    onConnect: (String, String, Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val platformUi = LocalPlatformUi.current
    val isUserAuthenticated by signInViewModel.isUserAuthenticated.collectAsState()
    val currentUser by signInViewModel.currentUser.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ThemedOutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            label = { Text(stringResource(Res.string.label_nickname)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        ThemedOutlinedTextField(
            value = nickServPasswordState.value,
            onValueChange = { nickServPasswordState.value = it },
            label = { Text(stringResource(Res.string.label_nickserv_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = rememberNickServPasswordState.value,
                onCheckedChange = { rememberNickServPasswordState.value = it }
            )
            Text(stringResource(Res.string.label_remember_password), color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                onConnect(nickname, nickServPasswordState.value, rememberNickServPasswordState.value)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(stringResource(Res.string.button_connect))
        }

        if (platformUi.supportsGoogleSignIn) {
            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            if (isUserAuthenticated) {
                Text(
                    text = stringResource(Res.string.account_signed_in_as, currentUser?.email ?: ""),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { signInViewModel.signOut() }) {
                    Text(stringResource(Res.string.account_sign_out))
                }
            } else {
                Text(
                    text = stringResource(Res.string.account_sign_in_hint),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                platformUi.GoogleSignInButton(onClick = onGoogleSignIn)
            }
        }
    }
}
