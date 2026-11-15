package com.jfaf.irc.data.prefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {
    @Test
    fun porDefectoEsOscuro() {
        assertEquals(ThemeMode.DARK, ThemeMode.Default)
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage(null))
    }

    @Test
    fun valorDesconocidoDaElPredeterminado() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("rojo"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage(""))
    }

    @Test
    fun cadaModoSeGuardaYSeLee() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStorage(it.name)) }
    }

    @Test
    fun oscuroYClaroIgnoranAlSistema() {
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
    }

    @Test
    fun delSistemaSigueAlSistema() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
    }
}
