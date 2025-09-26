package com.jfaf.irc.ui.screens.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Ensure this is imported
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jfaf.irc.R
import com.jfaf.irc.ui.theme.IRCTheme

@Composable
fun ConnectionSetupSection(
    nickname: String, onNicknameChange: (String) -> Unit,
    useSsl: Boolean, onUseSslChange: (Boolean) -> Unit,
    nickServPasswordState: MutableState<String>,
    rememberNickServPasswordState: MutableState<Boolean>,
    onConnect: (nick: String, ssl: Boolean, nickServPass: String, rememberPass: Boolean) -> Unit
) {
    val currentNickServPassword = nickServPasswordState.value
    val currentRememberNickServPassword = rememberNickServPasswordState.value

    // Define custom TextFieldColors once
    val customTextFieldColors: TextFieldColors = TextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onPrimary,
        unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
        disabledTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
        focusedIndicatorColor = MaterialTheme.colorScheme.onPrimary,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
        cursorColor = MaterialTheme.colorScheme.onPrimary,
        focusedLabelColor = MaterialTheme.colorScheme.onPrimary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
        focusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IRCTheme.gradientBrush)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "IRC Connect", 
                style = MaterialTheme.typography.displaySmall, 
                color = MaterialTheme.colorScheme.onPrimary, 
                fontWeight = FontWeight.Bold, 
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Conéctate y empieza a chatear", 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), 
                textAlign = TextAlign.Center
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = nickname, 
                onValueChange = onNicknameChange, 
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tu Nickname") },
                leadingIcon = { Icon(Icons.Filled.Person, "Nickname Icon") },
                shape = MaterialTheme.shapes.medium,
                singleLine = true, 
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = customTextFieldColors // Apply the defined colors
            )

            OutlinedTextField(
                value = nickServPasswordState.value,
                onValueChange = { nickServPasswordState.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(id = R.string.label_nickserv_password_optional)) },
                leadingIcon = { Icon(Icons.Filled.Lock, "Password Icon") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = customTextFieldColors // Apply the defined colors
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { rememberNickServPasswordState.value = !rememberNickServPasswordState.value }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = rememberNickServPasswordState.value, 
                    onCheckedChange = null, // Click handled by Row
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.onPrimary,
                        checkmarkColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                )
                Text(
                    text = stringResource(id = R.string.checkbox_label_remember_nickserv_password), 
                    color = MaterialTheme.colorScheme.onPrimary, 
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUseSslChange(!useSsl) }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = useSsl, 
                    onCheckedChange = null, // Click handled by Row
                     colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.onPrimary,
                        checkmarkColor = MaterialTheme.colorScheme.primary, 
                        uncheckedColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                )
                Text(
                    stringResource(R.string.checkbox_label_connect_securely), 
                    color = MaterialTheme.colorScheme.onPrimary, 
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        Button(
            onClick = { onConnect(nickname, useSsl, currentNickServPassword, currentRememberNickServPassword) }, 
            modifier = Modifier.fillMaxWidth().height(56.dp), 
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onPrimary,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(stringResource(R.string.button_connect), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
