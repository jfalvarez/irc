package com.jfaf.irc.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import com.jfaf.irc.ui.platform.LocalPlatformUi
import com.jfaf.irc.ui.platform.NoPlatformUi
import com.jfaf.irc.ui.platform.PlatformUi
import com.jfaf.irc.ui.screens.chat.MessageInputSection
import com.jfaf.irc.ui.theme.IRCAppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** El selector de emoticonos del campo de mensaje en escritorio: se abre desde el icono, inserta donde está el cursor y recuerda los recientes. */
@OptIn(ExperimentalTestApi::class)
class EmojiPickerUiTest {

    // 😃: está en la cuadrícula de «Caras» pero no es el icono de ninguna pestaña (😀 sí lo es, y saldría dos veces)
    private val smile = "😃"
    private val fire = "🔥" // 🔥

    private fun SemanticsNodeInteractionsProvider.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        platform: PlatformUi,
        recents: List<String> = emptyList(),
        onPicked: (String) -> Unit = {}
    ) = setContent {
        IRCAppTheme {
            CompositionLocalProvider(LocalPlatformUi provides platform) {
                MessageInputSection(
                    nickSuggestions = emptyList(),
                    onSendMessage = {},
                    onTextInputChanged = { _, _ -> },
                    onSuggestionSelected = { suggestion, _, _ -> suggestion },
                    onClearSuggestions = {},
                    recentEmojis = recents,
                    onEmojiPicked = onPicked,
                    autoFocus = true
                )
            }
        }
    }

    @Test
    fun enEscritorioHayUnIconoQueAbreElSelector() = runComposeUiTest {
        show(DesktopPlatformUi)

        assertEquals(0, count(smile), "El selector no debe verse hasta pulsar el icono")
        onNodeWithContentDescription("Insertar emoticono").performClick()
        waitForIdle()

        assertEquals(1, count(smile), "Debe verse la cuadrícula con los emoticonos de la primera categoría")
    }

    @Test
    fun enElMovilNoHayIconoPorqueElTecladoYaLoTrae() = runComposeUiTest {
        show(NoPlatformUi)
        assertEquals(0, onAllNodesWithContentDescription("Insertar emoticono").fetchSemanticsNodes().size)
    }

    @Test
    fun elegirUnoLoInsertaEnElCampoYCierraElSelector() = runComposeUiTest {
        val picked = mutableListOf<String>()
        show(DesktopPlatformUi, onPicked = { picked += it })

        onNodeWithContentDescription("Insertar emoticono").performClick()
        waitForIdle()
        onNodeWithText(smile).performClick()
        waitForIdle()

        onNode(hasSetTextAction()).assert(hasText(smile))
        assertEquals(listOf(smile), picked, "Debe avisarse para guardarlo en los recientes")
        assertEquals(1, count(smile), "Sólo queda el del campo: el selector se ha cerrado")
    }

    @Test
    fun seInsertaDondeEstaElCursorYNoAlFinal() = runComposeUiTest {
        show(DesktopPlatformUi)
        onNode(hasSetTextAction()).performTextInput("ab")
        onNode(hasSetTextAction()).performTextInputSelection(TextRange(1)) // entre la a y la b

        onNodeWithContentDescription("Insertar emoticono").performClick()
        waitForIdle()
        onNodeWithText(smile).performClick()
        waitForIdle()

        onNode(hasSetTextAction()).assert(hasText("a${smile}b"))
    }

    @Test
    fun sustituyeLoQueEsteSeleccionado() = runComposeUiTest {
        show(DesktopPlatformUi)
        onNode(hasSetTextAction()).performTextInput("hola mundo")
        onNode(hasSetTextAction()).performTextInputSelection(TextRange(5, 10)) // «mundo»

        onNodeWithContentDescription("Insertar emoticono").performClick()
        waitForIdle()
        onNodeWithText(smile).performClick()
        waitForIdle()

        onNode(hasSetTextAction()).assert(hasText("hola $smile"))
    }

    @Test
    fun escCierraElSelectorSinQuitarleElFocoAlCampo() = runComposeUiTest {
        show(DesktopPlatformUi)
        onNodeWithContentDescription("Insertar emoticono").performClick()
        waitForIdle()
        assertEquals(1, count(smile))

        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertEquals(0, count(smile), "Esc debe cerrar el selector")
        onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun pulsarFueraCierraElSelector() = runComposeUiTest {
        show(DesktopPlatformUi)
        onNodeWithContentDescription("Insertar emoticono").performClick()
        waitForIdle()
        assertEquals(1, count(smile))

        // Fuera del selector, que ocupa la esquina izquierda (360 dp); hay dos raíces (la ventana y el popup): la primera es la ventana
        onAllNodes(isRoot()).onFirst().performMouseInput { click(Offset(700f, 30f)) }
        waitForIdle()

        assertEquals(0, count(smile))
    }

    @Test
    fun seguirEscribiendoConElSelectorAbiertoFunciona() = runComposeUiTest {
        show(DesktopPlatformUi)
        onNodeWithContentDescription("Insertar emoticono").performClick()
        waitForIdle()

        onNode(hasSetTextAction()).performTextInput("hola")
        waitForIdle()

        onNode(hasSetTextAction()).assert(hasText("hola"))
        assertEquals(1, count(smile), "El selector sigue abierto")
    }

    @Test
    fun conRecientesElSelectorEmpiezaPorEllosYSePuedeCambiarDeCategoria() = runComposeUiTest {
        show(DesktopPlatformUi, recents = listOf(fire))

        onNodeWithContentDescription("Insertar emoticono").performClick()
        waitForIdle()
        assertEquals(1, count(fire), "Los recientes salen primero")
        assertEquals(0, count(smile), "Y la categoría de caras aún no")

        onNodeWithContentDescription("Caras").performClick()
        waitForIdle()
        assertEquals(1, count(smile))
        assertEquals(0, count(fire), "Al cambiar de categoría ya no se ven los recientes")
    }
}
