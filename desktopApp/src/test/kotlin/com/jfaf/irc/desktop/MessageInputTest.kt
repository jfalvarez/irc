package com.jfaf.irc.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import com.jfaf.irc.ui.screens.chat.MessageInputSection
import com.jfaf.irc.ui.theme.IRCAppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** El campo de mensaje con el teclado de escritorio: Enter envía, Tab completa el nick y el foco es automático. */
@OptIn(ExperimentalTestApi::class)
class MessageInputTest {

    @Test
    fun enterEnviaElMensajeYVaciaElCampo() = runComposeUiTest {
        var sent: String? = null
        setContent {
            IRCAppTheme {
                MessageInputSection(
                    nickSuggestions = emptyList(),
                    onSendMessage = { sent = it },
                    onTextInputChanged = { _, _ -> },
                    onSuggestionSelected = { suggestion, _, _ -> suggestion },
                    onClearSuggestions = {},
                    autoFocus = true
                )
            }
        }
        val field = onNode(hasSetTextAction())
        field.performTextInput("/join #hispano")
        field.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals("/join #hispano", sent, "Enter debe enviar el texto (también los comandos con /)")
        onNode(hasSetTextAction()).assert(!hasText("/join", substring = true))
    }

    @Test
    fun tabAceptaLaPrimeraSugerenciaDeNick() = runComposeUiTest {
        var picked: String? = null
        setContent {
            IRCAppTheme {
                MessageInputSection(
                    nickSuggestions = listOf("pepe", "pepa"),
                    onSendMessage = {},
                    onTextInputChanged = { _, _ -> },
                    onSuggestionSelected = { suggestion, _, _ -> picked = suggestion; "$suggestion: " },
                    onClearSuggestions = {},
                    autoFocus = true
                )
            }
        }
        val field = onNode(hasSetTextAction())
        field.performTextInput("pe")
        field.performKeyInput { pressKey(Key.Tab) }
        waitForIdle()

        assertEquals("pepe", picked)
        onNode(hasSetTextAction()).assert(hasText("pepe: "))
    }

    @Test
    fun sinSugerenciasTabNoHaceNada() = runComposeUiTest {
        var picked: String? = null
        setContent {
            IRCAppTheme {
                MessageInputSection(
                    nickSuggestions = emptyList(),
                    onSendMessage = {},
                    onTextInputChanged = { _, _ -> },
                    onSuggestionSelected = { suggestion, _, _ -> picked = suggestion; suggestion },
                    onClearSuggestions = {},
                    autoFocus = true
                )
            }
        }
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        assertNull(picked)
    }

    @Test
    fun conAutoFocusElCampoTieneElFoco() = runComposeUiTest {
        setContent {
            IRCAppTheme {
                MessageInputSection(
                    nickSuggestions = emptyList(),
                    onSendMessage = {},
                    onTextInputChanged = { _, _ -> },
                    onSuggestionSelected = { suggestion, _, _ -> suggestion },
                    onClearSuggestions = {},
                    autoFocus = true
                )
            }
        }
        waitForIdle()
        onNode(hasSetTextAction()).assertIsFocused()
    }
}
