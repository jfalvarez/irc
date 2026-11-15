package com.jfaf.irc.domain.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NickColorsTest {
    @Test
    fun elMismoNickDaSiempreElMismoIndice() {
        assertEquals(NickColors.paletteIndex("ana"), NickColors.paletteIndex("ana"))
    }

    @Test
    fun noDistingueMayusculasNiPrefijoDeModo() {
        val base = NickColors.paletteIndex("Kiko")
        assertEquals(base, NickColors.paletteIndex("kiko"))
        assertEquals(base, NickColors.paletteIndex("@Kiko"))
        assertEquals(base, NickColors.paletteIndex("+kiko"))
    }

    @Test
    fun elIndiceEstaEnLaPaleta() {
        listOf("", "a", "ana", "Kiko_99", "[x]", "ñandú", "a".repeat(200)).forEach {
            val i = NickColors.paletteIndex(it)
            assertTrue(i in 0 until NickColors.PALETTE_SIZE, "$it -> $i")
        }
    }

    @Test
    fun valoresConocidos() {
        // FNV-1a de 32 bits: el reparto no debe cambiar entre versiones (los colores de los usuarios son estables)
        assertEquals(9, NickColors.paletteIndex("ana"))
        assertEquals(3, NickColors.paletteIndex("kiko"))
    }

    @Test
    fun repartePorLaPaletaSinConcentrarse() {
        val nicks = (1..600).map { "user$it" }
        val counts = nicks.groupingBy { NickColors.paletteIndex(it) }.eachCount()
        assertEquals(NickColors.PALETTE_SIZE, counts.size)
        assertTrue(counts.values.all { it in 20..90 }, counts.toString())
    }

    @Test
    fun inicialDelAvatar() {
        assertEquals("A", NickColors.initial("ana"))
        assertEquals("K", NickColors.initial("@kiko"))
        assertEquals("X", NickColors.initial("[x]"))
        assertEquals("7", NickColors.initial("_7up"))
        assertEquals("[", NickColors.initial("[]"))
        assertEquals("?", NickColors.initial(""))
        assertEquals("?", NickColors.initial("@"))
    }
}
