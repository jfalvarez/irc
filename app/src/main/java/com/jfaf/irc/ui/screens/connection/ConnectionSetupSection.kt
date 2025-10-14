package com.jfaf.irc.ui.screens.connection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.jfaf.irc.MainActivity
import com.jfaf.irc.R
import com.jfaf.irc.ui.common.ThemedOutlinedTextField
import com.jfaf.irc.ui.viewmodels.SignInViewModel

@Composable
fun ConnectionSetupSection(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    useSsl: Boolean,
    onUseSslChange: (Boolean) -> Unit,
    nickServPasswordState: MutableState<String>,
    rememberNickServPasswordState: MutableState<Boolean>,
    signInViewModel: SignInViewModel,
    mainActivity: MainActivity,
    onConnect: (String, Boolean, String, Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
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
            label = { Text(stringResource(R.string.label_nickname)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        ThemedOutlinedTextField(
            value = nickServPasswordState.value,
            onValueChange = { nickServPasswordState.value = it },
            label = { Text(stringResource(R.string.label_nickserv_password)) },
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
                checked = useSsl,
                onCheckedChange = onUseSslChange
            )
            Text(stringResource(R.string.label_use_ssl), color = MaterialTheme.colorScheme.onBackground)
            Checkbox(
                checked = rememberNickServPasswordState.value,
                onCheckedChange = { rememberNickServPasswordState.value = it }
            )
            Text(stringResource(R.string.label_remember_password), color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                onConnect(nickname, useSsl, nickServPasswordState.value, rememberNickServPasswordState.value)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(stringResource(R.string.button_connect))
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(24.dp))

        if (isUserAuthenticated) {
            Text(
                text = "Has iniciado sesión como: ${currentUser?.email ?: ""}",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { signInViewModel.signOut() }) {
                Text("Cerrar sesión")
            }
        } else {
            Text(
                text = "Inicia sesión con Google para sincronizar tu configuración (ej. lista de ignorados) entre dispositivos.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken("634830856305-8i1qkiu2o3nj57hhft9dsd17oeqlulr5.apps.googleusercontent.com")
                        .requestEmail()
                        .build()
                    val googleSignInClient = GoogleSignIn.getClient(mainActivity, gso)
                    mainActivity.signInLauncher.launch(googleSignInClient.signInIntent)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_google_logo),
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text("Iniciar sesión con Google")
            }
        }
    }
}
