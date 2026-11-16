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
    fun unCanalSinNivelPropio_sigueElPredeterminadoQueSeLePase() {
        val levels = mapOf("#chat" to MUTED)
        assertEquals(ALL, ChannelNotificationLevels.levelFor(levels, "#otro", ALL))
        assertEquals(MUTED, ChannelNotificationLevels.levelFor(levels, "#CHAT", ALL), "El propio manda sobre el predeterminado")
    }

    @Test
    fun elegirUnNivelLoGuarda_tambienSiCoincideConElPredeterminado_yNullLoQuita() {
        val levels = ChannelNotificationLevels.with(emptyMap(), "#Chat", ALL)
        assertEquals(mapOf("#chat" to ALL), levels)
        // Elegir «Solo menciones» a propósito se conserva: no cambia solo si luego cambia el predeterminado
        val explicit = ChannelNotificationLevels.with(levels, "#CHAT", MENTIONS)
        assertEquals(mapOf("#chat" to MENTIONS), explicit)
        assertEquals(emptyMap(), ChannelNotificationLevels.with(explicit, "#CHAT", null))
    }

    @Test
    fun seGuardaUnTextoPorCanal() {
        val encoded = ChannelNotificationLevels.encode(mapOf("#a" to ALL, "#b" to MUTED, "#c" to MENTIONS))
        assertEquals(setOf("ALL:#a", "MUTED:#b", "MENTIONS:#c"), encoded)
    }

    @Test
    fun silenciadosEntreLosCanalesAbiertos_contandoElPredeterminado() {
        val targets = listOf("Servidor", "#Uno", "#dos", "ana")
        // Sin predeterminado silenciado: sólo los que lo piden
        assertEquals(setOf("#dos"), ChannelNotificationLevels.mutedAmong(targets, mapOf("#dos" to MUTED), MENTIONS))
        // Con el predeterminado en Silenciado, todos los canales (no privados ni Servidor) salvo los que eligieron otro
        assertEquals(setOf("#dos"), ChannelNotificationLevels.mutedAmong(targets, mapOf("#uno" to ALL), MUTED))
        assertEquals(emptySet(), ChannelNotificationLevels.mutedAmong(targets, emptyMap(), ALL))
    }

    @Test
    fun codificarYDecodificarEsReversible() {
        val levels = mapOf("#a" to ALL, "#b" to MUTED, "#c-d_e" to ALL)
        assertEquals(levels, ChannelNotificationLevels.decode(ChannelNotificationLevels.encode(levels)))
    }

    @Test
    fun decodificarIgnoraLoMalFormado() {
        val decoded = ChannelNotificationLevels.decode(setOf("ALL:#bien", "ENORME:#mal", "MUTED:sinalmohadilla", "ALL", ""))
        assertEquals(mapOf("#bien" to ALL), decoded)
        assertTrue(ChannelNotificationLevels.decode(null).isEmpty())
    }

    @Test
    fun unCanalConDosPuntosEnElNombreNoRompeLaLectura() {
        val levels = mapOf("#a:b" to MUTED)
        assertEquals(levels, ChannelNotificationLevels.decode(ChannelNotificationLevels.encode(levels)))
    }
}
