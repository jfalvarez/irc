package com.jfaf.irc.data.prefs

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageDensityTest {
    @Test
    fun porDefectoEsComoda() {
        assertEquals(MessageDensity.COMFORTABLE, MessageDensity.Default)
        assertEquals(MessageDensity.COMFORTABLE, MessageDensity.fromStorage(null))
    }

    @Test
    fun valorDesconocidoDaElPredeterminado() {
        assertEquals(MessageDensity.COMFORTABLE, MessageDensity.fromStorage("enorme"))
        assertEquals(MessageDensity.COMFORTABLE, MessageDensity.fromStorage(""))
    }

    @Test
    fun cadaDensidadSeGuardaYSeLee() {
        MessageDensity.entries.forEach { assertEquals(it, MessageDensity.fromStorage(it.name)) }
    }
}
