package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.jfaf.irc.R
import com.jfaf.irc.ui.theme.IRCTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInputSection(
    modifier: Modifier = Modifier,
    nickSuggestions: List<String>,
    onSendMessage: (String) -> Unit,
    onTextInputChanged: (text: String, cursorPosition: Int) -> Unit,
    onSuggestionSelected: (suggestion: String, currentFullText: String, cursorPosition: Int) -> String,
    onClearSuggestions: () -> Unit
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.primary, shadowElevation = 4.dp) {
        var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
        val keyboardController = LocalSoftwareKeyboardController.current
        var showSuggestions by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(nickSuggestions) {
            showSuggestions = nickSuggestions.isNotEmpty()
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                TextField(
                    value = textFieldValue,
                    onValueChange = {
                        val oldValue = textFieldValue
                        textFieldValue = it
                        if (oldValue.text != it.text || oldValue.selection != it.selection) {
                            onTextInputChanged(it.text, it.selection.start)
                        }
                    },
                    placeholder = { Text(stringResource(R.string.label_write_message), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)) },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (textFieldValue.text.isNotBlank()) {
                                onSendMessage(textFieldValue.text)
                                textFieldValue = TextFieldValue("")
                                onClearSuggestions()
                                showSuggestions = false
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                stringResource(R.string.cd_send_message),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused && !showSuggestions) {
                                onClearSuggestions()
                            }
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textFieldValue.text.isNotBlank()) {
                            onSendMessage(textFieldValue.text)
                            textFieldValue = TextFieldValue("")
                            keyboardController?.hide()
                            onClearSuggestions()
                            showSuggestions = false
                        }
                    }),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                        unfocusedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        focusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                        disabledContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                        cursorColor = MaterialTheme.colorScheme.onPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                )

                if (showSuggestions && nickSuggestions.isNotEmpty()) {
                    DropdownMenu(
                        expanded = true, 
                        onDismissRequest = {
                            onClearSuggestions()
                            showSuggestions = false
                        },
                        modifier = Modifier
                            .background(IRCTheme.dropdownMenuContainerOpaque)
                            .fillMaxWidth(0.8f) 
                            .align(Alignment.BottomStart), 
                        properties = PopupProperties(focusable = false) 
                    ) {
                        nickSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    val originalText = textFieldValue.text
                                    val originalCursorPos = textFieldValue.selection.start
                                    val newTextString = onSuggestionSelected(suggestion, originalText, originalCursorPos)

                                    val textBeforeCursorInOriginal = originalText.substring(0, originalCursorPos)
                                    val lastSpaceBeforeWord = textBeforeCursorInOriginal.lastIndexOf(' ')
                                    val prefixLength = if (lastSpaceBeforeWord == -1) 0 else lastSpaceBeforeWord + 1
                                    val newCursorPos = prefixLength + suggestion.length + 1 

                                    textFieldValue = TextFieldValue(
                                        text = newTextString,
                                        selection = TextRange(minOf(newCursorPos, newTextString.length))
                                    )
                                    
                                    onClearSuggestions() 
                                    showSuggestions = false
                                    focusRequester.requestFocus() 
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}