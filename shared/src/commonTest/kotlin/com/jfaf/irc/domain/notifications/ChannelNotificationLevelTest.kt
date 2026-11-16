package com.jfaf.irc.domain.notifications

import com.jfaf.irc.domain.notifications.ChannelNotificationLevel.ALL
import com.jfaf.irc.domain.notifications.ChannelNotificationLevel.MENTIONS
import com.jfaf.irc.domain.notifications.ChannelNotificationLevel.MUTED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelNotificationLevelTest {

    @Test
    fun porDefectoSoloMenciones() {
        assertEquals(MENTIONS, ChannelNotificationLevel.Default)
        assertEquals(MENTIONS, ChannelNotificationLevel.fromStorage(null))
        assertEquals(MENTIONS, ChannelNotificationLevel.fromStorage("loquesea"))
    }

    @Test
    fun cadaNivelSeGuardaYSeLee() {
        ChannelNotificationLevel.entries.forEach { assertEquals(it, ChannelNotificationLevel.fromStorage(it.name)) }
    }

    @Test
    fun unCanalSinTocarTieneElNivelPorDefecto_sinDistinguirMayusculas() {
        val levels = mapOf("#chat" to MUTED)
        assertEquals(MUTED, ChannelNotificationLevels.levelFor(levels, "#Chat"))
        assertEquals(MENTIONS, ChannelNotificationLevels.levelFor(levels, "#otro"))
    }

    @Test
    fun cambiarAlNivelPorDefectoLoQuitaDelMapa() {
        val levels = ChannelNotificationLevels.with(emptyMap(), "#Chat", ALL)
        assertEquals(mapOf("#chat" to ALL), levels)
        assertEquals(emptyMap(), ChannelNotificationLevels.with(levels, "#CHAT", MENTIONS))
    }

    @Test
    fun seGuardaUnTextoPorCanal_yNoSeGuardaLoPredeterminado() {
        val encoded = ChannelNotificationLevels.encode(mapOf("#a" to ALL, "#b" to MUTED, "#c" to MENTIONS))
        assertEquals(setOf("ALL:#a", "MUTED:#b"), encoded)
    }

    @Test
    fun codificarYDecodificarEsReversible() {
        val levels = mapOf("#a" to ALL, "#b" to MUTED, "#c-d_e" to ALL)
        assertEquals(levels, ChannelNotificationLevels.decode(ChannelNotificationLevels.encode(levels)))
    }

    @Test
    fun decodificarIgnoraLoMalFormado() {
        val decoded = ChannelNotificationLevels.decode(setOf("ALL:#bien", "ENORME:#mal", "MUTED:sinalmohadilla", "ALL", "", "MENTIONS:#x"))
        assertEquals(mapOf("#bien" to ALL), decoded)
        assertTrue(ChannelNotificationLevels.decode(null).isEmpty())
    }

    @Test
    fun unCanalConDosPuntosEnElNombreNoRompeLaLectura() {
        val levels = mapOf("#a:b" to MUTED)
        assertEquals(levels, ChannelNotificationLevels.decode(ChannelNotificationLevels.encode(levels)))
    }
}
