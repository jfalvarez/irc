package com.jfaf.irc.domain.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class InputAutocompleteTest {

    private val users = listOf("@Alice", "+Bob", "carl")
    private val channels = listOf("#general", "#Kotlin", "#kotlin-es", "#hispano", "#general")

    private fun suggest(
        text: String,
        cursor: Int = text.length,
        target: String? = "#general",
        known: List<String> = channels,
        onKnownRequested: () -> Unit = {}
    ) = InputAutocomplete.suggestions(target, users, { onKnownRequested(); known }, text, cursor, ownNick = "Yo")

    // --- comandos ---
    @Test
    fun sugiereComandosPorPrefijo_sinDistinguirMayusculas() {
        assertEquals(listOf("/join"), suggest("/jo"))
        assertEquals(listOf("/msg", "/me"), suggest("/m"))
        assertEquals(listOf("/query", "/quit"), suggest("/qu"))
        assertEquals(listOf("/join"), suggest("/JO"))
    }

    @Test
    fun unaBarraSolaSugiereLosMasUsados() {
        assertEquals(listOf("/join", "/msg", "/me", "/part", "/query"), suggest("/"))
    }

    @Test
    fun noSugiereUnComandoYaEscritoEntero_niLoQueNoExiste() {
        assertEquals(emptyList(), suggest("/join"))
        assertEquals(emptyList(), suggest("/zzz"))
    }

    @Test
    fun losComandosSoloSeSugierenEnLaPrimeraPalabra() {
        assertEquals(emptyList(), suggest("hola /jo"))
        assertEquals(emptyList(), suggest("/join /jo"))
    }

    @Test
    fun losComandosSeSugierenEnCualquierPestana() {
        assertEquals(listOf("/join"), suggest("/jo", target = "Servidor"))
        assertEquals(listOf("/join"), suggest("/jo", target = "ana"))
        assertEquals(listOf("/join"), suggest("/jo", target = null))
    }

    @Test
    fun usaLaPalabraHastaElCursor() {
        assertEquals(listOf("/join"), suggest("/jo argumentos", cursor = 3))
    }

    // --- canales ---
    @Test
    fun sugiereCanalesPorPrefijo_conSuMayusculas_sinRepetidos() {
        assertEquals(listOf("#general"), suggest("#ge"))
        assertEquals(listOf("#Kotlin", "#kotlin-es"), suggest("#kot"))
        assertEquals(listOf("#Kotlin", "#kotlin-es"), suggest("#KOT"))
    }

    @Test
    fun conSoloLaAlmohadilla_noSugiere_yNoSugiereLoYaEscritoEntero() {
        assertEquals(emptyList(), suggest("#"))
        assertEquals(emptyList(), suggest("#hispano"))
    }

    @Test
    fun loscanalesSeSugierenEnCualquierPalabra_yEnCualquierPestana() {
        assertEquals(listOf("#hispano"), suggest("entra en #his"))
        assertEquals(listOf("#hispano"), suggest("/join #his", target = "ana"))
    }

    @Test
    fun limitaLasSugerencias() {
        val many = (1..20).map { "#canal$it" }
        assertEquals(InputAutocomplete.MAX_SUGGESTIONS, suggest("#can", known = many).size)
        assertEquals(InputAutocomplete.MAX_SUGGESTIONS, suggest("/").size)
    }

    @Test
    fun laListaDeCanalesSoloSePideConUnaAlmohadilla() {
        var requested = 0
        suggest("hola ca", onKnownRequested = { requested++ })
        suggest("/jo", onKnownRequested = { requested++ })
        assertEquals(0, requested)
        suggest("#ge", onKnownRequested = { requested++ })
        assertEquals(1, requested)
    }

    // --- nicks: lo de siempre ---
    @Test
    fun losNicksSiguenIgual() {
        assertEquals(listOf("carl"), suggest("hola ca"))
        assertEquals(listOf("Alice"), suggest("@al"))
        assertEquals(emptyList(), suggest("hola ca", target = "ana"))
        assertFalse(suggest("hola ca").any { it.startsWith("/") || it.startsWith("#") })
    }

    @Test
    fun aplicarUnaSugerenciaSustituyeLaPalabraYAnadeUnEspacio() {
        assertEquals("/join ", NickAutocomplete.apply("/join", "/jo", 3))
        assertEquals("/join #kotlin ", NickAutocomplete.apply("#kotlin", "/join #ko", 9))
        assertEquals(6, NickAutocomplete.cursorAfterApply("/join", "/jo", 3))
    }
}
