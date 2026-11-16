package com.jfaf.irc.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.jfaf.irc.ui.theme.IRCAppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** UI-29: el factor de tamaño de texto del tema agranda las letras (sp) sin tocar las medidas en dp. */
@OptIn(ExperimentalTestApi::class)
class TextScaleUiTest {

    @Test
    fun elFactorEscalaElTexto() = runSkikoComposeUiTest {
        var scale by mutableStateOf(1f)
        setContent { IRCAppTheme(textScale = scale) { Text("Hola mundo") } }
        val normal = onNodeWithText("Hola mundo").getBoundsInRoot()

        scale = 1.3f
        waitForIdle()
        val big = onNodeWithText("Hola mundo").getBoundsInRoot()
        assertTrue(big.width.value > normal.width.value * 1.2f, "El texto grande debe ser más ancho: $normal -> $big")
        assertTrue(big.height.value > normal.height.value * 1.2f, "...y más alto: $normal -> $big")

        scale = 0.85f
        waitForIdle()
        val small = onNodeWithText("Hola mundo").getBoundsInRoot()
        assertTrue(small.width.value < normal.width.value, "El texto pequeño debe ser más estrecho: $normal -> $small")
    }
}
