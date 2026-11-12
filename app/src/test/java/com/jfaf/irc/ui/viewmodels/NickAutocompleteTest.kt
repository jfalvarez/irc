package com.jfaf.irc.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NickAutocompleteTest {

    private val users = listOf("Alice", "alan", "Bob", "albert", "alfa", "alma", "alba", "al")

    @Test
    fun sinCanalActivo_noSugiere() {
        assertTrue(NickAutocomplete.suggestions(null, users, "al", 2).isEmpty())
        assertTrue(NickAutocomplete.suggestions("Bob", users, "al", 2).isEmpty())
    }

    @Test
    fun palabraDeUnaLetra_noSugiere() {
        assertTrue(NickAutocomplete.suggestions("#c", users, "a", 1).isEmpty())
    }

    @Test
    fun filtraPorPrefijo_sinMayusculas_excluyeCoincidenciaExacta_yLimitaA5() {
        val result = NickAutocomplete.suggestions("#c", users, "hola al", 7)
        assertEquals(listOf("Alice", "alan", "albert", "alfa", "alma"), result)
    }

    @Test
    fun usaSoloLaPalabraAnteriorAlCursor() {
        val result = NickAutocomplete.suggestions("#c", users, "bo y mas", 2)
        assertEquals(listOf("Bob"), result)
    }

    @Test
    fun apply_sustituyeLaPalabraYAnadeEspacio() {
        assertEquals("hola Alice ", NickAutocomplete.apply("Alice", "hola al", 7))
    }

    @Test
    fun apply_conTextoPosteriorAlCursor() {
        assertEquals("Alice resto", NickAutocomplete.apply("Alice", "al   resto", 2))
    }
}
