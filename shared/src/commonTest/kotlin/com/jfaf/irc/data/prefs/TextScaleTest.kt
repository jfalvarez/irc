package com.jfaf.irc.data.prefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextScaleTest {
    @Test
    fun porDefectoEsNormal() {
        assertEquals(TextScale.NORMAL, TextScale.Default)
        assertEquals(1.0f, TextScale.Default.factor)
        assertEquals(TextScale.NORMAL, TextScale.fromStorage(null))
    }

    @Test
    fun valorDesconocidoDaElPredeterminado() {
        assertEquals(TextScale.NORMAL, TextScale.fromStorage("enorme"))
        assertEquals(TextScale.NORMAL, TextScale.fromStorage(""))
    }

    @Test
    fun cadaTamanoSeGuardaYSeLee() {
        TextScale.entries.forEach { assertEquals(it, TextScale.fromStorage(it.name)) }
    }

    @Test
    fun losFactoresCrecenEnOrden_yElNormalNoCambiaNada() {
        val factors = TextScale.entries.map { it.factor }
        assertEquals(factors.sorted(), factors)
        assertEquals(factors.size, factors.distinct().size)
        assertTrue(TextScale.SMALL.factor < 1f && TextScale.EXTRA_LARGE.factor > 1f)
    }
}
