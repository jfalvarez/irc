package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.chat.QuickSwitcher.Entry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickSwitcherTest {

    private val targets = listOf("Servidor", "#general", "#dev-kotlin", "#música", "ana", "kiko")

    private fun search(
        query: String,
        active: String? = "#general",
        unread: Set<String> = emptySet(),
        mentions: Map<String, Int> = emptyMap(),
        pms: Map<String, Int> = emptyMap(),
        friends: Set<String> = emptySet(),
        list: List<String> = targets,
        muted: Set<String> = emptySet()
    ) = QuickSwitcher.search(list, active, unread, mentions, pms, friends, query, muted)

    private fun names(entries: List<Entry>) = entries.map {
        when (it) {
            is Entry.Tab -> it.target
            is Entry.OpenPrivate -> "→${it.nick}"
            is Entry.JoinChannel -> "+${it.channel}"
        }
    }

    @Test
    fun sinTexto_saleTodoMenosLaPestanaActiva() {
        assertEquals(listOf("Servidor", "#dev-kotlin", "#música", "ana", "kiko"), names(search("")))
    }

    @Test
    fun sinTexto_laUrgenciaVaPrimero_menciones_privados_sinLeer() {
        val r = search("", unread = setOf("#música"), mentions = mapOf("#dev-kotlin" to 2), pms = mapOf("kiko" to 1))
        assertEquals(listOf("#dev-kotlin", "kiko", "#música", "Servidor", "ana"), names(r))
    }

    @Test
    fun buscaSinDistinguirMayusculasNiLaAlmohadilla() {
        // Lo mejor va primero; al final, la acción de escribir a ese nick (lo escrito no es un canal abierto)
        assertEquals(listOf("#dev-kotlin", "→KOT"), names(search("KOT")))
        assertEquals(listOf("#dev-kotlin", "→dev"), names(search("dev")))
    }

    @Test
    fun ordenaPorCercania_igual_empiezaPor_palabra_contiene() {
        val list = listOf("#xkot", "#kotlin", "#dev-kot", "#kot", "ana")
        val r = names(search("kot", active = null, list = list))
        assertEquals(listOf("#kot", "#kotlin", "#dev-kot", "#xkot"), r.filterNot { it.startsWith("+") || it.startsWith("→") })
    }

    @Test
    fun conAlmohadillaSoloBuscaCanales() {
        val r = names(search("#a", list = listOf("Servidor", "#general", "#ayuda", "ana"), active = null))
        assertTrue("ana" !in r)
        assertTrue("#ayuda" in r)
    }

    @Test
    fun ofreceUnirseAUnCanalQueNoEsta() {
        assertEquals("+#nuevo", names(search("#nuevo")).last())
    }

    @Test
    fun noOfreceUnirseAUnCanalQueYaEsta() {
        assertFalse(names(search("#música")).any { it.startsWith("+") })
        assertFalse(names(search("#MÚSICA")).any { it.startsWith("+") })
    }

    @Test
    fun ofreceEscribirAUnNickQueNoTieneConversacion() {
        assertEquals("→pepe", names(search("pepe")).last())
        assertTrue(names(search("ana")).none { it.startsWith("→") }, "ana ya tiene pestaña")
    }

    @Test
    fun losAmigosConectadosSinPestanaSalenComoPrivado() {
        val r = search("", friends = setOf("luis", "ana"))
        assertTrue(Entry.OpenPrivate("luis", isFriend = true) in r)
        assertTrue(r.none { it is Entry.OpenPrivate && it.nick == "ana" }, "ana ya tiene pestaña")
        // Un amigo escrito entero no se repite como nick suelto
        assertEquals(1, search("luis", friends = setOf("luis")).count { it is Entry.OpenPrivate })
    }

    @Test
    fun conAlmohadillaNoSalenAmigos() {
        assertTrue(search("#", friends = setOf("luis")).none { it is Entry.OpenPrivate })
    }

    @Test
    fun noOfreceAccionesParaLoQueNoParecenCanalNiNick() {
        assertTrue(search("dos palabras").isEmpty())
        assertTrue(search("#a,b").none { it is Entry.JoinChannel })
        assertTrue(search("   ").none { it is Entry.JoinChannel || (it is Entry.OpenPrivate && !it.isFriend) })
    }

    @Test
    fun limitaLosResultadosPeroConservaLaAccion() {
        val many = (1..20).map { "#canal$it" }
        val r = QuickSwitcher.search(many, null, emptySet(), emptyMap(), emptyMap(), emptySet(), "#canal")
        assertEquals(QuickSwitcher.MAX_RESULTS, r.count { it is Entry.Tab })
        val withAction = QuickSwitcher.search(many, null, emptySet(), emptyMap(), emptyMap(), emptySet(), "#nuevo")
        assertEquals(listOf(Entry.JoinChannel("#nuevo")), withAction)
    }

    @Test
    fun unCanalSilenciado_noCuentaComoSinLeerNiMenciones_yNoSubeEnElOrden() {
        val all = search(
            "",
            unread = setOf("#dev-kotlin", "kiko"),
            mentions = mapOf("#música" to 2),
            muted = setOf("#música", "#dev-kotlin")
        )
        // Sólo kiko (sin leer, no es un canal silenciado) sube; los canales silenciados quedan en su orden normal
        assertEquals(listOf("kiko", "Servidor", "#dev-kotlin", "#música", "ana"), names(all))
        val dev = all.filterIsInstance<Entry.Tab>().first { it.target == "#dev-kotlin" }
        val music = all.filterIsInstance<Entry.Tab>().first { it.target == "#música" }
        assertFalse(dev.unread)
        assertFalse(music.unread)
        assertEquals(0, music.mentions)
    }

    @Test
    fun silenciarNoAfectaAOtrosCanalesNiAPrivados() {
        val all = search("", unread = setOf("#dev-kotlin", "ana"), mentions = mapOf("#música" to 1), muted = setOf("#dev-kotlin"))
        val tabs = all.filterIsInstance<Entry.Tab>().associateBy { it.target }
        assertFalse(tabs.getValue("#dev-kotlin").unread)
        assertTrue(tabs.getValue("#música").unread)
        assertEquals(1, tabs.getValue("#música").mentions)
        assertTrue(tabs.getValue("ana").unread)
    }

    @Test
    fun cuentaLosNoLeidosSinDistinguirMayusculas() {
        val r = search("kiko", pms = mapOf("KIKO" to 3)).filterIsInstance<Entry.Tab>().single()
        assertEquals(3, r.unreadMessages)
        assertTrue(r.unread)
    }
}
