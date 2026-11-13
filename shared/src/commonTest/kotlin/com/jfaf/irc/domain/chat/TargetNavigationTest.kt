package com.jfaf.irc.domain.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TargetNavigationTest {

    private val targets = listOf("Servidor", "#a", "#b", "pepe")

    @Test
    fun siguienteAvanzaYDaLaVuelta() {
        assertEquals("#a", TargetNavigation.neighbour(targets, "Servidor", forward = true))
        assertEquals("Servidor", TargetNavigation.neighbour(targets, "pepe", forward = true))
    }

    @Test
    fun anteriorRetrocedeYDaLaVuelta() {
        assertEquals("#a", TargetNavigation.neighbour(targets, "#b", forward = false))
        assertEquals("pepe", TargetNavigation.neighbour(targets, "Servidor", forward = false))
    }

    @Test
    fun sinPestanaActivaEmpiezaPorUnExtremo() {
        assertEquals("Servidor", TargetNavigation.neighbour(targets, null, forward = true))
        assertEquals("pepe", TargetNavigation.neighbour(targets, null, forward = false))
        assertEquals("Servidor", TargetNavigation.neighbour(targets, "#ya-cerrada", forward = true))
    }

    @Test
    fun conUnaSolaPestanaNoHayAdondeIr() {
        assertNull(TargetNavigation.neighbour(listOf("Servidor"), "Servidor", forward = true))
        assertNull(TargetNavigation.neighbour(emptyList(), null, forward = true))
    }

    @Test
    fun ignoraDuplicados() {
        assertEquals("#a", TargetNavigation.neighbour(listOf("Servidor", "Servidor", "#a"), "Servidor", forward = true))
    }
}
