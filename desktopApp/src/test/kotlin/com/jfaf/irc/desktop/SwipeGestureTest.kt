package com.jfaf.irc.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jfaf.irc.ui.common.openOnLeftSwipe
import kotlin.test.Test
import kotlin.test.assertEquals

/** El gesto que abre la lista de usuarios: de derecha a izquierda desde cualquier punto, sin estorbar a nada más. */
@OptIn(ExperimentalTestApi::class)
class SwipeGestureTest {

    @Test
    fun soloUnArrastreDeDerechaAIzquierdaLoDispara() = runSkikoComposeUiTest {
        var opened = 0
        setContent {
            Box(Modifier.size(300.dp).testTag("zona").openOnLeftSwipe(enabled = true) { opened++ }) {
                Text("contenido")
            }
        }
        val zona = onNodeWithTag("zona")

        zona.performTouchInput { swipeRight() }
        assertEquals(0, opened, "De izquierda a derecha no abre los usuarios (eso es del cajón de pestañas)")
        zona.performTouchInput { swipeDown() }
        assertEquals(0, opened, "Un arrastre vertical tampoco")
        zona.performTouchInput { click() }
        assertEquals(0, opened, "Ni un toque")

        zona.performTouchInput { swipeLeft() }
        assertEquals(1, opened, "De derecha a izquierda, sí, y una sola vez")
    }

    @Test
    fun desactivadoNoHaceNada() = runSkikoComposeUiTest {
        var opened = 0
        setContent {
            Box(Modifier.size(300.dp).testTag("zona").openOnLeftSwipe(enabled = false) { opened++ }) { Text("x") }
        }
        onNodeWithTag("zona").performTouchInput { swipeLeft() }
        assertEquals(0, opened)
    }

    @Test
    fun noEstorbaAlDesplazamientoVerticalDeUnaLista() = runSkikoComposeUiTest {
        var opened = 0
        setContent {
            Box(Modifier.size(300.dp).openOnLeftSwipe(enabled = true) { opened++ }) {
                LazyColumn(Modifier.testTag("lista")) { items((1..50).toList()) { Text("fila $it", Modifier.size(48.dp)) } }
            }
        }
        onNodeWithTag("lista").performTouchInput { swipeUp() }
        assertEquals(0, opened)
        // y la lista se movió: la primera fila ya no se ve
        assertEquals(0, onAllNodesWithText("fila 1").fetchSemanticsNodes().size)
    }
}
