package com.jfaf.irc.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jfaf.irc.domain.user.NickListSearch
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.cd_navigate_up
import com.jfaf.irc.resources.user_list_count_filtered
import com.jfaf.irc.resources.user_list_no_matches
import com.jfaf.irc.resources.user_list_search_hint
import org.jetbrains.compose.resources.stringResource
import com.jfaf.irc.ui.common.ThemedOutlinedTextField

/**
 * Pantalla genérica de una lista de nicks (ignorados, amigos): buscador arriba, que además sirve
 * para añadir, y la lista en un `LazyColumn` (sólo se dibuja lo que se ve, aunque haya miles).
 *
 * @param users nicks guardados, en minúsculas.
 * @param addLabel texto del botón de añadir para el nick escrito (recibe el nick).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    title: String,
    users: Set<String>,
    emptyText: String,
    removeButtonText: String,
    addLabel: @Composable (nick: String) -> String,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onNavigateUp: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val sorted = remember(users) { NickListSearch.sorted(users) }
    val visible = remember(sorted, query) { NickListSearch.filter(sorted, query) }
    val nickToAdd = NickListSearch.nickToAdd(query, users)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun add(nick: String) {
        onAdd(nick)
        query = ""
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_navigate_up)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            item {
                ThemedOutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.user_list_search_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { nickToAdd?.let(::add) })
                )
            }
            if (users.isNotEmpty()) {
                item {
                    Text(
                        text = if (query.isBlank()) users.size.toString()
                        else stringResource(Res.string.user_list_count_filtered, visible.size, users.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
            }
            if (nickToAdd != null) {
                item {
                    Button(
                        onClick = { add(nickToAdd) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) { Text(addLabel(nickToAdd)) }
                }
            }
            if (users.isEmpty()) {
                item {
                    Text(
                        text = emptyText,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else if (visible.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.user_list_no_matches),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(visible, key = { it }) { nick ->
                    UserListRow(nick = nick, removeButtonText = removeButtonText, onRemove = { onRemove(nick) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun UserListRow(nick: String, removeButtonText: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = nick,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRemove) { Text(removeButtonText) }
    }
}
