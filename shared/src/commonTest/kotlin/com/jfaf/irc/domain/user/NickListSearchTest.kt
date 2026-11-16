package com.jfaf.irc.domain.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NickListSearchTest {
    private val nicks = setOf("pepe", "Ana", "kiko", "ana_bcn")

    @Test
    fun ordenaSinDistinguirMayusculas() {
        assertEquals(listOf("Ana", "ana_bcn", "kiko", "pepe"), NickListSearch.sorted(nicks))
    }

    @Test
    fun sinTextoDevuelveTodos() {
        val sorted = NickListSearch.sorted(nicks)
        assertEquals(sorted, NickListSearch.filter(sorted, ""))
        assertEquals(sorted, NickListSearch.filter(sorted, "  "))
    }

    @Test
    fun filtraPorSubcadenaSinDistinguirMayusculas() {
        val sorted = NickListSearch.sorted(nicks)
        assertEquals(listOf("Ana", "ana_bcn"), NickListSearch.filter(sorted, "AN"))
        assertEquals(listOf("kiko"), NickListSearch.filter(sorted, " ik "))
        assertEquals(emptyList(), NickListSearch.filter(sorted, "zzz"))
    }

    @Test
    fun nickToAddRecortaYDevuelveElTexto() {
        assertEquals("Luis", NickListSearch.nickToAdd("  Luis ", setOf("pepe")))
    }

    @Test
    fun nickToAddRechazaVacioEspaciosYRepetidos() {
        val existing = setOf("pepe")
        assertNull(NickListSearch.nickToAdd("", existing))
        assertNull(NickListSearch.nickToAdd("   ", existing))
        assertNull(NickListSearch.nickToAdd("dos palabras", existing))
        assertNull(NickListSearch.nickToAdd("PEPE", existing))
    }
}
