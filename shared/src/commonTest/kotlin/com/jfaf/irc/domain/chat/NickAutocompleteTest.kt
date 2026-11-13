package com.jfaf.irc.domain.chat

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

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

    // --- #27: casos reales. La lista de NAMES trae los nicks con el prefijo de modo (@op, +voz).
    private val withModes = listOf("@Alice", "+Bob", "carl", "@dani")

    @Test
    fun operadoresYVoces_sugierenSinEscribirElPrefijo() {
        assertEquals(listOf("Alice"), NickAutocomplete.suggestions("#c", withModes, "hola al", 7))
        assertEquals(listOf("Bob"), NickAutocomplete.suggestions("#c", withModes, "bo", 2))
    }

    @Test
    fun laSugerenciaNuncaLlevaElPrefijoDeModo() {
        val all = NickAutocomplete.suggestions("#c", withModes, "hola @a", 7) + NickAutocomplete.suggestions("#c", withModes, "ca", 2)
        assertTrue(all.none { it.startsWith("@") || it.startsWith("+") }, "El nick insertado no puede llevar @ ni +: $all")
    }

    @Test
    fun conElCursorEnMitadDePalabra_apply_sustituyeLaPalabraEntera() {
        // "hola al|ice" (cursor tras "al"): antes quedaba "hola Alice ice"
        assertEquals("hola Alice ", NickAutocomplete.apply("Alice", "hola alice", 7))
    }

    @Test
    fun noSugiereElPropioNick() {
        assertEquals(listOf("alan"), NickAutocomplete.suggestions("#c", listOf("Alice", "alan"), "al", 2, ownNick = "alice"))
    }

    @Test
    fun reconocePrefijosDeModoMultiples() {
        val multi = listOf("~owner", "&admin", "%half", "+voz", "@op")
        assertEquals(listOf("owner"), NickAutocomplete.suggestions("#c", multi, "ow", 2))
        assertEquals(listOf("admin"), NickAutocomplete.suggestions("#c", multi, "ad", 2))
        assertEquals(listOf("half"), NickAutocomplete.suggestions("#c", multi, "ha", 2))
    }

    @Test
    fun noRepiteUnNickPresenteVariasVeces() {
        assertEquals(listOf("Alice"), NickAutocomplete.suggestions("#c", listOf("@Alice", "Alice"), "al", 2))
    }

    @Test
    fun laMencionConArrobaSeConservaYSeCompletaElNick() {
        assertEquals(listOf("Alice"), NickAutocomplete.suggestions("#c", withModes, "hola @al", 8))
        assertEquals("hola @Alice ", NickAutocomplete.apply("Alice", "hola @al", 8))
    }

    @Test
    fun cursorAfterApply_quedaTrasElEspacioInsertado() {
        // "hola al" -> "hola Alice |" (11 caracteres)
        assertEquals(11, NickAutocomplete.cursorAfterApply("Alice", "hola al", 7))
        // con mención: "hola @al" -> "hola @Alice |" (12 caracteres)
        assertEquals(12, NickAutocomplete.cursorAfterApply("Alice", "hola @al", 8))
        // sin texto posterior, el cursor queda al final del texto resultante
        assertEquals(NickAutocomplete.apply("Alice", "hola @al", 8).length, NickAutocomplete.cursorAfterApply("Alice", "hola @al", 8))
        // palabra al principio: "al" -> "Alice |"
        assertEquals(6, NickAutocomplete.cursorAfterApply("Alice", "al", 2))
    }
}
