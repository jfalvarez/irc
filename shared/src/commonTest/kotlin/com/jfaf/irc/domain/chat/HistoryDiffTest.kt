package com.jfaf.irc.domain.chat

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class HistoryDiffTest {

    private val server = "Servidor"

    @Test
    fun sinCambios_noHayNadaQueEscribir() {
        val state = mapOf("#a" to listOf("1", "2"), "ana" to listOf("hola"))
        assertTrue(HistoryDiff.compute(state, state, server).isEmpty)
    }

    @Test
    fun soloSeReescribenLosTargetsModificadosONuevos() {
        val last = mapOf("#a" to listOf("1"), "ana" to listOf("hola"))
        val now = mapOf("#a" to listOf("1", "2"), "ana" to listOf("hola"), "#b" to listOf("x"))
        val changes = HistoryDiff.compute(last, now, server)
        assertEquals(setOf("#a", "#b"), changes.replace.keys)
        assertTrue(changes.delete.isEmpty())
    }

    @Test
    fun targetsDesaparecidosSeBorran() {
        val changes = HistoryDiff.compute(mapOf("#a" to listOf("1"), "ana" to listOf("x")), mapOf("#a" to listOf("1")), server)
        assertEquals(setOf("ana"), changes.delete)
        assertTrue(changes.replace.isEmpty())
    }

    @Test
    fun laPestanaServidorNuncaSeGuarda() {
        val changes = HistoryDiff.compute(emptyMap(), mapOf(server to listOf("MOTD")), server)
        assertTrue(changes.isEmpty)
        assertTrue(HistoryDiff.compute(mapOf(server to listOf("MOTD")), emptyMap<String, List<String>>(), server).isEmpty)
    }

    @Test
    fun vaciarUnTarget_seReescribeVacio() {
        val changes = HistoryDiff.compute(mapOf("#a" to listOf("1")), mapOf("#a" to emptyList()), server)
        assertEquals(mapOf("#a" to emptyList<String>()), changes.replace)
    }
}
