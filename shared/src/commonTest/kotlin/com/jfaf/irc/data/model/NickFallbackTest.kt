package com.jfaf.irc.data.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class NickFallbackTest {

    @Test
    fun secuenciaDeAlternativas() {
        assertEquals("ElTester_", NickFallback.alternative("ElTester", 1))
        assertEquals("ElTester__", NickFallback.alternative("ElTester", 2))
        assertEquals("ElTester_1", NickFallback.alternative("ElTester", 3))
        assertEquals("ElTester_2", NickFallback.alternative("ElTester", 4))
    }

    @Test
    fun seAgotan() {
        assertNull(NickFallback.alternative("ElTester", 0))
        assertNull(NickFallback.alternative("ElTester", NickFallback.MAX_ATTEMPTS + 1))
    }

    @Test
    fun noSuperaLaLongitudMaxima() {
        val long = "a".repeat(40)
        assertEquals(30, NickFallback.alternative(long, 2)!!.length)
    }
}
