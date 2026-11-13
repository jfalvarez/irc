package com.jfaf.irc.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GifTimingTest {

    @Test
    fun losFotogramasMuyRapidosVanA100ms_comoEnLosNavegadores() {
        assertEquals(100L, gifFrameDelayMs(0))
        assertEquals(100L, gifFrameDelayMs(10))
    }

    @Test
    fun losDemasRespetanSuDuracion() {
        assertEquals(11L, gifFrameDelayMs(11))
        assertEquals(70L, gifFrameDelayMs(70))
        assertEquals(2_000L, gifFrameDelayMs(2_000))
    }

    @Test
    fun repeticionesDeSkia_aPasadasCompletas() {
        assertNull(gifTotalPlays(-1)) // sin fin
        assertEquals(1, gifTotalPlays(0)) // sin extensión de bucle: una sola vez
        assertEquals(3, gifTotalPlays(2)) // se repite 2 veces más
    }
}
