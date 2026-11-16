package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.jfaf.irc.domain.chat.ChatSearch
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.cd_search_close
import com.jfaf.irc.resources.cd_search_newer
import com.jfaf.irc.resources.cd_search_older
import com.jfaf.irc.resources.chat_search_count
import com.jfaf.irc.resources.chat_search_hint
import com.jfaf.irc.resources.chat_search_no_results
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Estado de la búsqueda dentro del chat (UI-35): si está abierta, lo escrito, el resultado actual (0 = el más
 * reciente) y cuántos hay. Lo calcula `MessagesList`, que es quien conoce la línea de tiempo, y lo escribe en
 * [matchCount]; la barra y los atajos sólo lo leen.
 */
@Stable
class ChatSearchState {
    var active by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    var position by mutableIntStateOf(0)
    var matchCount by mutableIntStateOf(0)

    fun open() {
        active = true
    }

    fun close() {
        active = false
        query = ""
        position = 0
        matchCount = 0
    }

    fun updateQuery(text: String) {
        query = text
        position = 0 // al cambiar la consulta, se empieza por el resultado más reciente
    }

    /** Pasa al resultado más antiguo ([towardsOlder]) o al más reciente, dando la vuelta al llegar al final. */
    fun step(towardsOlder: Boolean) {
        position = ChatSearch.step(matchCount, position, towardsOlder)
    }
}

/**
 * Barra de búsqueda: ocupa el sitio de la barra superior del chat mientras la búsqueda está abierta. Intro va al
 * resultado más antiguo siguiente (Mayús+Intro, al más reciente) y Esc la cierra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchBar(state: ChatSearchState) {
    val focusRequester = remember { FocusRequester() }
    // El campo aún puede no estar colocado en el primer instante: se reintenta hasta que acepta el foco
    LaunchedEffect(Unit) {
        withFrameNanos { }
        repeat(20) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(30)
        }
    }
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    TopAppBar(
        title = {
            BasicTextField(
                value = state.query,
                onValueChange = state::updateQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = onPrimary),
                cursorBrush = SolidColor(onPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { state.step(towardsOlder = true) }),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                state.step(towardsOlder = !event.isShiftPressed)
                                true
                            }
                            Key.Escape -> {
                                state.close()
                                true
                            }
                            else -> false
                        }
                    },
                decorationBox = { inner ->
                    Box {
                        if (state.query.isEmpty()) {
                            Text(stringResource(Res.string.chat_search_hint), style = MaterialTheme.typography.bodyLarge, color = onPrimary.copy(alpha = 0.75f))
                        }
                        inner()
                    }
                }
            )
        },
        navigationIcon = {
            IconButton(onClick = state::close) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.cd_search_close))
            }
        },
        actions = {
            if (state.query.isNotBlank()) {
                Text(
                    text = if (state.matchCount == 0) stringResource(Res.string.chat_search_no_results)
                    else stringResource(Res.string.chat_search_count, state.position + 1, state.matchCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = onPrimary,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            IconButton(onClick = { state.step(towardsOlder = true) }, enabled = state.matchCount > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(Res.string.cd_search_older))
            }
            IconButton(onClick = { state.step(towardsOlder = false) }, enabled = state.matchCount > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, stringResource(Res.string.cd_search_newer))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = onPrimary,
            navigationIconContentColor = onPrimary,
            actionIconContentColor = onPrimary
        )
    )
}
