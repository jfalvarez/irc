package com.jfaf.irc.ui.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ByteLruCacheTest {

    private fun bytes(n: Int) = ByteArray(n) { it.toByte() }

    @Test
    fun guardaYDevuelve() {
        val cache = ByteLruCache(100)
        cache.put("a", bytes(10))
        assertContentEquals(bytes(10), cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals(10, cache.sizeBytes())
    }

    @Test
    fun alPasarseDelTopeDescartaLoMenosUsado() {
        val cache = ByteLruCache(100)
        cache.put("a", bytes(40))
        cache.put("b", bytes(40))
        cache.get("a") // "a" pasa a ser el más reciente: el menos usado es "b"
        cache.put("c", bytes(40))

        assertNull(cache.get("b"))
        assertContentEquals(bytes(40), cache.get("a"))
        assertContentEquals(bytes(40), cache.get("c"))
        assertEquals(80, cache.sizeBytes())
    }

    @Test
    fun reemplazarUnaClaveAjustaElTamano() {
        val cache = ByteLruCache(100)
        cache.put("a", bytes(60))
        cache.put("a", bytes(30))
        assertEquals(30, cache.sizeBytes())
    }

    @Test
    fun loQueNoCabeNiVacioNoSeGuardaYNoExpulsaNada() {
        val cache = ByteLruCache(100)
        cache.put("a", bytes(50))
        cache.put("grande", bytes(101))
        assertNull(cache.get("grande"))
        assertContentEquals(bytes(50), cache.get("a"))
    }
}
