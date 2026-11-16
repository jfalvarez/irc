package com.jfaf.irc.ui.screens.chat

import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import com.jfaf.irc.ui.theme.IRCWhite
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.EmojiEmotions
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.jfaf.irc.domain.chat.EmojiInsertion
import com.jfaf.irc.domain.chat.InputInsertion
import com.jfaf.irc.domain.chat.MessageActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.jfaf.irc.domain.chat.NickAutocomplete
import com.jfaf.irc.ui.emoji.EmojiPicker
import com.jfaf.irc.ui.platform.LocalPlatformUi
import com.jfaf.irc.ui.theme.IRCTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInputSection(
    modifier: Modifier = Modifier,
    nickSuggestions: List<String>,
    onSendMessage: (String) -> Unit,
    onTextInputChanged: (text: String, cursorPosition: Int) -> Unit,
    onSuggestionSelected: (suggestion: String, currentFullText: String, cursorPosition: Int) -> String,
    onClearSuggestions: () -> Unit,
    /** Escritorio: los emoticonos usados últimamente, para la categoría «Recientes» del selector. */
    recentEmojis: List<String> = emptyList(),
    onEmojiPicked: (String) -> Unit = {},
    /** Menciones y citas que pide el menú de un mensaje: se insertan en el cursor y el campo recibe el foco. */
    insertions: Flow<InputInsertion> = emptyFlow(),
    /** Pone el foco en el campo al aparecer y cada vez que cambia [focusKey] (escritorio; en el móvil abriría el teclado). */
    autoFocus: Boolean = false,
    focusKey: Any? = null
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.primary, shadowElevation = 4.dp) {
        var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
        val keyboardController = LocalSoftwareKeyboardController.current
        var showSuggestions by remember { mutableStateOf(false) }
        var showEmojiPicker by remember { mutableStateOf(false) }
        // En el móvil el teclado ya trae sus emoticonos; en escritorio, no.
        val showEmojiButton = LocalPlatformUi.current.isDesktop
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(nickSuggestions) {
            showSuggestions = nickSuggestions.isNotEmpty()
        }

        LaunchedEffect(focusKey, autoFocus) {
            if (autoFocus) runCatching { focusRequester.requestFocus() }
        }

        fun applySuggestion(suggestion: String) {
            val originalText = textFieldValue.text
            val originalCursorPos = textFieldValue.selection.start
            val newTextString = onSuggestionSelected(suggestion, originalText, originalCursorPos)

            val newCursorPos = NickAutocomplete.cursorAfterApply(suggestion, originalText, originalCursorPos)

            textFieldValue = TextFieldValue(
                text = newTextString,
                selection = TextRange(minOf(newCursorPos, newTextString.length))
            )

            onClearSuggestions()
            showSuggestions = false
            focusRequester.requestFocus()
        }

        LaunchedEffect(insertions) {
            insertions.collect { insertion ->
                val value = textFieldValue
                val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
                val piece = MessageActions.render(insertion, value.text.substring(0, start), value.text.isBlank())
                val result = EmojiInsertion.insert(value.text, value.selection.start, value.selection.end, piece)
                textFieldValue = TextFieldValue(text = result.text, selection = TextRange(result.cursor))
                onTextInputChanged(result.text, result.cursor)
                runCatching { focusRequester.requestFocus() }
            }
        }

        // Inserta el emoticono donde está el cursor (o sustituye la selección) y devuelve el foco al campo.
        fun insertEmoji(emoji: String) {
            val value = textFieldValue
            val result = EmojiInsertion.insert(value.text, value.selection.start, value.selection.end, emoji)
            textFieldValue = TextFieldValue(text = result.text, selection = TextRange(result.cursor))
            onTextInputChanged(result.text, result.cursor)
            onEmojiPicked(emoji)
            showEmojiPicker = false
            focusRequester.requestFocus()
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
                    placeholder = { Text(stringResource(Res.string.label_write_message), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = if (showEmojiButton) {
                        {
                            // Sin foco propio: el campo lo conserva y con él lo que hubiera seleccionado
                            IconButton(
                                onClick = { showEmojiPicker = !showEmojiPicker },
                                modifier = Modifier.focusProperties { canFocus = false }
                            ) {
                                Icon(Icons.Outlined.EmojiEmotions, stringResource(Res.string.cd_insert_emoji))
                            }
                        }
                    } else {
                        null
                    },
                    trailingIcon = {
                        // Botón de enviar en píldora con el degradado de la marca (UI-17); apagado si no hay texto
                        val canSend = textFieldValue.text.isNotBlank()
                        IconButton(
                            onClick = {
                                if (canSend) {
                                    onSendMessage(textFieldValue.text)
                                    textFieldValue = TextFieldValue("")
                                    onClearSuggestions()
                                    showSuggestions = false
                                }
                            },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(width = 48.dp, height = 36.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(IRCTheme.gradientBrush, alpha = if (canSend) 1f else 0.4f)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                stringResource(Res.string.cd_send_message),
                                tint = IRCWhite
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            // Esc cierra el selector de emoticonos (no tiene foco propio)
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && showEmojiPicker) {
                                showEmojiPicker = false
                                return@onPreviewKeyEvent true
                            }
                            // Tab acepta la primera sugerencia de nick (autocompletado)
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Tab && !event.isCtrlPressed &&
                                showSuggestions && nickSuggestions.isNotEmpty()
                            ) {
                                applySuggestion(nickSuggestions.first())
                                true
                            } else {
                                false
                            }
                        }
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
                    shape = RoundedCornerShape(percent = 50), // píldora (UI-17)
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                        focusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                                onClick = { applySuggestion(suggestion) }
                            )
                        }
                    }
                }

                if (showEmojiPicker) {
                    EmojiPicker(
                        recents = recentEmojis,
                        onPick = ::insertEmoji,
                        onDismiss = { showEmojiPicker = false }
                    )
                }
            }
        }
    }
}