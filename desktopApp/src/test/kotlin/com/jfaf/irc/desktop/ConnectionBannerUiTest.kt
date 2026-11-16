package com.jfaf.irc.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.ui.screens.chat.ConnectionBannerView
import com.jfaf.irc.ui.theme.IRCAppTheme
import kotlin.test.Test

/** UI-28: el banner de conexión según el estado, sin servidor: «Reconectando…», sin Internet, recuperada y rendida. */
@OptIn(ExperimentalTestApi::class)
class ConnectionBannerUiTest {

    @Test
    fun siguelasFasesDeLaReconexion() = runSkikoComposeUiTest {
        var reconnecting by mutableStateOf(false)
        var waiting by mutableStateOf(false)
        var connected by mutableStateOf(true)
        setContent {
            IRCAppTheme { ConnectionBannerView(reconnecting, waiting, connected) }
        }
        // El indicador de progreso se anima sin parar: el reloj se avanza a mano
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(500)
        onNodeWithText("Reconectando…").assertDoesNotExist()

        reconnecting = true
        mainClock.advanceTimeBy(500)
        onNodeWithText("Reconectando…").assertExists()

        waiting = true
        mainClock.advanceTimeBy(500)
        onNodeWithText("Sin conexión a Internet", substring = true).assertExists()

        // Vuelve la red y se reconecta: aviso de «Conexión recuperada» y luego desaparece
        waiting = false
        reconnecting = false
        mainClock.advanceTimeBy(500)
        onNodeWithText("Conexión recuperada").assertExists()
        mainClock.advanceTimeBy(4000)
        onNodeWithText("Conexión recuperada").assertDoesNotExist()
        onNodeWithText("Reconectando…").assertDoesNotExist()
    }

    @Test
    fun siSeRinde_noDiceQueSeRecupero() = runSkikoComposeUiTest {
        var reconnecting by mutableStateOf(true)
        var connected by mutableStateOf(false)
        setContent {
            IRCAppTheme { ConnectionBannerView(reconnecting, isWaitingForNetwork = false, isConnected = connected) }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(500)
        onNodeWithText("Reconectando…").assertExists()

        reconnecting = false // abandona: no hay enlace
        mainClock.advanceTimeBy(500)
        onNodeWithText("Conexión recuperada").assertDoesNotExist()
        mainClock.advanceTimeBy(1000)
        onNodeWithText("Reconectando…").assertDoesNotExist()
    }
}
