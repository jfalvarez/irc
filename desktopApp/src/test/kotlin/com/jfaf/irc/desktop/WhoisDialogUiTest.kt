package com.jfaf.irc.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.ui.screens.chat.WhoisDialog
import com.jfaf.irc.ui.theme.IRCAppTheme
import com.jfaf.irc.ui.viewmodels.WhoisUiState
import kotlin.test.Test
import kotlin.test.assertEquals

/** El diálogo de WHOIS cuando el servidor no responde: lo dice y deja reintentar. */
@OptIn(ExperimentalTestApi::class)
class WhoisDialogUiTest {

    @Test
    fun sinRespuesta_avisaYReintentaConElMismoNick() = runSkikoComposeUiTest {
        val retried = mutableListOf<String>()
        setContent {
            IRCAppTheme { WhoisDialog(WhoisUiState.TimedOut("ana"), onJoinChannel = {}, onDismiss = {}, onRetry = { retried += it }) }
        }
        onNodeWithText("El servidor no ha respondido", substring = true).assertExists()

        onNodeWithText("Reintentar").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("ana"), retried)
    }

    @Test
    fun mientrasEspera_noOfreceReintentar() = runSkikoComposeUiTest {
        setContent {
            IRCAppTheme { WhoisDialog(WhoisUiState.Loading("ana"), onJoinChannel = {}, onDismiss = {}) }
        }
        onNodeWithText("Consultando a ana…").assertExists()
        onNodeWithText("Reintentar").assertDoesNotExist()
    }
}
