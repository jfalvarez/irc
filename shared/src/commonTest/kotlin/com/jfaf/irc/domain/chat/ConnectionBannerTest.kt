package com.jfaf.irc.domain.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionBannerTest {

    @Test
    fun sinReconexion_noHayBanner() {
        assertEquals(ConnectionBannerState.HIDDEN, ConnectionBanner.of(reconnecting = false, waitingForNetwork = false))
        // Esperar a la red sólo tiene sentido reconectando
        assertEquals(ConnectionBannerState.HIDDEN, ConnectionBanner.of(reconnecting = false, waitingForNetwork = true))
    }

    @Test
    fun reconectando_distingueSinInternet() {
        assertEquals(ConnectionBannerState.RECONNECTING, ConnectionBanner.of(reconnecting = true, waitingForNetwork = false))
        assertEquals(ConnectionBannerState.WAITING_FOR_NETWORK, ConnectionBanner.of(reconnecting = true, waitingForNetwork = true))
    }

    @Test
    fun recuperada_soloAlTerminarUnaReconexionConEnlace() {
        assertTrue(ConnectionBanner.isRestored(wasReconnecting = true, reconnecting = false, connected = true))
        // Se rindió: ya no reconecta pero tampoco hay enlace
        assertFalse(ConnectionBanner.isRestored(wasReconnecting = true, reconnecting = false, connected = false))
        // Sigue reconectando, o nunca lo estuvo (primera conexión)
        assertFalse(ConnectionBanner.isRestored(wasReconnecting = true, reconnecting = true, connected = true))
        assertFalse(ConnectionBanner.isRestored(wasReconnecting = false, reconnecting = false, connected = true))
    }

    @Test
    fun elCampoSoloSeApagaReconectando() {
        assertFalse(ConnectionBanner.isInputEnabled(reconnecting = true))
        assertTrue(ConnectionBanner.isInputEnabled(reconnecting = false))
    }
}
