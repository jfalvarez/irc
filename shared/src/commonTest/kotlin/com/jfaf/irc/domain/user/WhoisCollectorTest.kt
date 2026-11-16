package com.jfaf.irc.domain.user

import com.jfaf.irc.data.model.ParsedIrcMessage
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhoisCollectorTest {

    private val collector = WhoisCollector()
    private val results = mutableListOf<WhoisResult>()

    private fun numeric(command: String, vararg params: String, trailing: String? = null) =
        ParsedIrcMessage(rawLine = "", prefix = "irc.red.net", command = command, params = listOf("yo") + params, trailing = trailing)

    /** Alimenta el colector con cada mensaje y devuelve si lo consumió. */
    private fun feed(vararg messages: ParsedIrcMessage): List<Boolean> {
        return messages.map { collector.accept(it) }
    }

    @Test
    fun agrupaLasRespuestasEnUnResultado() = runBlocking {
        val job = collector.results.onEach { results += it }.launchIn(this)
        kotlinx.coroutines.yield()
        collector.expect("Ana")

        val consumed = feed(
            numeric("311", "Ana", "ana", "host.ejemplo.com", "*", trailing = "Ana Pérez"),
            numeric("319", "Ana", trailing = "@#uno +#dos #tres"),
            numeric("312", "Ana", "irc.red.net", trailing = "Red de pruebas"),
            numeric("330", "Ana", "ana_cuenta", trailing = "is logged in as"),
            numeric("671", "Ana", trailing = "is using a secure connection"),
            numeric("313", "Ana", trailing = "is an IRC Operator"),
            numeric("301", "Ana", trailing = "de vacaciones"),
            numeric("317", "Ana", "125", "1700000000", trailing = "seconds idle, signon time"),
            numeric("318", "Ana", trailing = "End of /WHOIS list.")
        )
        kotlinx.coroutines.yield()
        job.cancel()

        assertTrue(consumed.all { it })
        val r = results.single()
        assertTrue(r.found)
        assertEquals("Ana", r.nick)
        assertEquals("ana", r.user)
        assertEquals("host.ejemplo.com", r.host)
        assertEquals("Ana Pérez", r.realName)
        assertEquals("irc.red.net", r.server)
        assertEquals("Red de pruebas", r.serverInfo)
        assertEquals(listOf("@#uno", "+#dos", "#tres"), r.channels)
        assertEquals("ana_cuenta", r.account)
        assertTrue(r.isSecure)
        assertTrue(r.isOperator)
        assertEquals("de vacaciones", r.awayMessage)
        assertEquals(125L, r.idleSeconds)
        assertEquals(1700000000L, r.signOnEpochSeconds)
    }

    @Test
    fun variosCanales319SeAcumulan() = runBlocking {
        collector.expect("ana")
        feed(
            numeric("311", "ana", "u", "h", "*", trailing = "x"),
            numeric("319", "ana", trailing = "#a #b"),
            numeric("319", "ana", trailing = "#c")
        )
        val job = collector.results.onEach { results += it }.launchIn(this)
        kotlinx.coroutines.yield()
        feed(numeric("318", "ana", trailing = "fin"))
        kotlinx.coroutines.yield()
        job.cancel()
        assertEquals(listOf("#a", "#b", "#c"), results.single().channels)
    }

    @Test
    fun sinExpect_noConsumeNada() {
        val consumed = feed(
            numeric("311", "ana", "u", "h", "*", trailing = "x"),
            numeric("318", "ana", trailing = "fin")
        )
        assertEquals(listOf(false, false), consumed)
    }

    @Test
    fun nickDeOtraConsulta_noSeConsume() {
        collector.expect("ana")
        assertFalse(collector.accept(numeric("311", "pepe", "u", "h", "*", trailing = "x")))
    }

    @Test
    fun noDistingueMayusculas() {
        collector.expect("ANA")
        assertTrue(collector.accept(numeric("311", "ana", "u", "h", "*", trailing = "x")))
    }

    @Test
    fun usuarioInexistente_401PasaAlChatYElCierreSeConsume() = runBlocking {
        val job = collector.results.onEach { results += it }.launchIn(this)
        kotlinx.coroutines.yield()
        collector.expect("nadie")

        // El 401 sigue su camino (deja el aviso en el Servidor); el 318 posterior se traga
        assertFalse(collector.accept(numeric("401", "nadie", trailing = "No such nick/channel")))
        assertTrue(collector.accept(numeric("318", "nadie", trailing = "End of /WHOIS list.")))
        kotlinx.coroutines.yield()
        job.cancel()

        val r = results.single()
        assertFalse(r.found)
        assertEquals("nadie", r.nick)
        assertEquals(emptyList(), r.channels)
        assertNull(r.host)
    }

    @Test
    fun cancelar_devuelveLasLineasAlChat() {
        collector.expect("ana")
        collector.cancel("ana")
        assertFalse(collector.accept(numeric("311", "ana", "u", "h", "*", trailing = "x")))
    }

    @Test
    fun otrosComandos_seIgnoran() {
        collector.expect("ana")
        assertFalse(collector.accept(ParsedIrcMessage(rawLine = "", command = "PRIVMSG", params = listOf("yo", "ana"), trailing = "hola")))
        assertFalse(collector.accept(numeric("433", "ana", trailing = "Nickname is already in use")))
    }

    @Test
    fun segundaConsultaDelMismoNick_reiniciaLosDatos() = runBlocking {
        collector.expect("ana")
        feed(numeric("311", "ana", "u", "h", "*", trailing = "x"), numeric("319", "ana", trailing = "#viejo"))
        collector.expect("ana")
        feed(numeric("311", "ana", "u", "h", "*", trailing = "x"))
        val job = collector.results.onEach { results += it }.launchIn(this)
        kotlinx.coroutines.yield()
        feed(numeric("318", "ana", trailing = "fin"))
        kotlinx.coroutines.yield()
        job.cancel()
        assertEquals(emptyList(), results.single().channels)
    }

    @Test
    fun duracionCompacta() {
        assertEquals("0 s", formatCompactDuration(-5))
        assertEquals("45 s", formatCompactDuration(45))
        assertEquals("3 min", formatCompactDuration(180))
        assertEquals("2 h 5 min", formatCompactDuration(2 * 3600 + 5 * 60 + 30))
        assertEquals("2 h", formatCompactDuration(7200))
        assertEquals("1 d 4 h", formatCompactDuration(86_400 + 4 * 3600 + 120))
        assertEquals("3 d", formatCompactDuration(3 * 86_400))
    }

    @Test
    fun canalSinPrefijosDeEstado() {
        assertEquals("#uno", "@#uno".channelWithoutStatusPrefix())
        assertEquals("#dos", "+#dos".channelWithoutStatusPrefix())
        assertEquals("#tres", "@+#tres".channelWithoutStatusPrefix())
        assertEquals("#cuatro", "#cuatro".channelWithoutStatusPrefix())
    }
}
