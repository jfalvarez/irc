package com.jfaf.irc.domain.channel

import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelListSearchTest {

    private val entries = listOf(
        ChannelListEntry("#Hispano", 120, "nt", "Canal general"),
        ChannelListEntry("#kotlin", 30, "nt", "Programación en Kotlin"),
        ChannelListEntry("#android", 30, "nt", "Desarrollo móvil"),
        ChannelListEntry("#cine", 8, "nt", "Películas y series"),
        ChannelListEntry("#vacio", 0, "", "")
    )

    private fun names(query: String, sort: ChannelListSort = ChannelListSort.BY_USERS, plain: (String) -> String = { it }) =
        ChannelListSearch.filterAndSort(entries, query, sort, plain).map { it.name }

    @Test
    fun sinTexto_ordenaPorUsuariosYLuegoPorNombre() {
        assertEquals(listOf("#Hispano", "#android", "#kotlin", "#cine", "#vacio"), names(""))
    }

    @Test
    fun ordenPorNombre_ignoraMayusculas() {
        assertEquals(listOf("#android", "#cine", "#Hispano", "#kotlin", "#vacio"), names("", ChannelListSort.BY_NAME))
    }

    @Test
    fun buscaEnElNombre_sinMayusculasNiAlmohadilla() {
        assertEquals(listOf("#Hispano"), names("hispa"))
        assertEquals(listOf("#Hispano"), names("#HISPA"))
        assertEquals(listOf("#cine"), names("  cin  "))
    }

    @Test
    fun buscaEnElTema() {
        assertEquals(listOf("#kotlin"), names("programación"))
        assertEquals(listOf("#cine"), names("series"))
    }

    @Test
    fun buscaEnElTemaLimpio_noEnLosCodigosDeColor() {
        val colored = listOf(ChannelListEntry("#a", 1, "", "\u000304Rojo\u0003 y verde"))
        val strip = { t: String -> t.replace(Regex("\u0003\\d{0,2}"), "") }
        // «04Rojo» no debe casar con «04»
        assertEquals(emptyList(), ChannelListSearch.filterAndSort(colored, "04", ChannelListSort.BY_USERS, strip))
        assertEquals(listOf("#a"), ChannelListSearch.filterAndSort(colored, "rojo y", ChannelListSort.BY_USERS, strip).map { it.name })
    }

    @Test
    fun quitaLosCanalesSenuelo() {
        val decoy = "Fake channel for confusing spambots"
        val mixed = entries + listOf(
            ChannelListEntry("#cIF35JjzKlwxXWne", 3143, "", decoy),
            ChannelListEntry("#F85", 2056, "", "\u000304fake channel\u0003 for confusing spambots")
        )
        val strip = { t: String -> t.replace(Regex("\u0003\\d{0,2}"), "") }
        assertEquals(entries.map { it.name }, ChannelListSearch.withoutDecoys(mixed, strip).map { it.name })
        assertEquals(true, ChannelListSearch.isDecoy(mixed[5], strip))
        assertEquals(false, ChannelListSearch.isDecoy(entries[0], strip))
        assertEquals(emptyList(), ChannelListSearch.withoutDecoys(mixed.drop(5), strip))
    }

    @Test
    fun sinCoincidencias_devuelveVacio() {
        assertEquals(emptyList(), names("zzz"))
    }
}
