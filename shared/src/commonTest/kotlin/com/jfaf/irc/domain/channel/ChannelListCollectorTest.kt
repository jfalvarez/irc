package com.jfaf.irc.domain.channel

import com.jfaf.irc.data.model.ParsedIrcMessage
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelListCollectorTest {

    private val collector = ChannelListCollector()
    private val results = mutableListOf<ChannelListResult>()

    private fun numeric(command: String, vararg params: String, trailing: String? = null) =
        ParsedIrcMessage(rawLine = "", prefix = "irc.red.net", command = command, params = listOf("yo") + params, trailing = trailing)

    private fun entry(name: String, users: Int, topic: String) = numeric("322", name, users.toString(), trailing = topic)

    private suspend fun collecting(block: suspend () -> Unit) = kotlinx.coroutines.coroutineScope {
        val job = collector.results.onEach { results += it }.launchIn(this)
        yield()
        block()
        yield()
        job.cancel()
    }

    @Test
    fun sinConsultaAnunciada_noConsumeNada() {
        assertFalse(collector.accept(numeric("321", "Channel", trailing = "Users  Name")))
        assertFalse(collector.accept(entry("#a", 3, "[+nt] hola")))
        assertFalse(collector.accept(numeric("323", trailing = "End of /LIST")))
        assertFalse(collector.isCollecting)
    }

    @Test
    fun agrupaLaListaEnUnResultado() = runBlocking {
        collecting {
            collector.expect()
            val consumed = listOf(
                collector.accept(numeric("321", "Channel", trailing = "Users  Name")),
                collector.accept(entry("#hispano", 120, "[+nt] Bienvenidos al canal")),
                collector.accept(entry("#privado", 4, "[+ntk] Sólo con clave")),
                collector.accept(entry("#sinmodos", 7, "Un tema sin modos")),
                collector.accept(entry("#vacio", 1, "")),
                collector.accept(numeric("323", trailing = "End of /LIST"))
            )
            assertTrue(consumed.all { it })
        }
        val r = results.single()
        assertEquals(ChannelListStatus.COMPLETE, r.status)
        assertEquals(
            listOf(
                ChannelListEntry("#hispano", 120, "nt", "Bienvenidos al canal"),
                ChannelListEntry("#privado", 4, "ntk", "Sólo con clave"),
                ChannelListEntry("#sinmodos", 7, "", "Un tema sin modos"),
                ChannelListEntry("#vacio", 1, "", "")
            ),
            r.entries
        )
        assertTrue(r.entries[1].hasKey)
        assertFalse(r.entries[0].hasKey)
        assertFalse(collector.isCollecting)
    }

    @Test
    fun elCierreDejaDeConsumir() = runBlocking {
        collecting {
            collector.expect()
            collector.accept(numeric("323", trailing = "End of /LIST"))
        }
        assertFalse(collector.accept(entry("#tarde", 1, "x")))
    }

    @Test
    fun ignoraOtrasRespuestas() {
        collector.expect()
        assertFalse(collector.accept(numeric("311", "ana", "u", "h", "*", trailing = "Ana")))
        assertFalse(collector.accept(ParsedIrcMessage(rawLine = "", command = "PRIVMSG", params = listOf("#a"), trailing = "hola")))
        assertTrue(collector.isCollecting)
    }

    @Test
    fun usuariosNoNumericos_cuentanComoCero() = runBlocking {
        collecting {
            collector.expect()
            collector.accept(numeric("322", "#raro", "muchos", trailing = "tema"))
            collector.accept(numeric("323"))
        }
        assertEquals(0, results.single().entries.single().users)
    }

    @Test
    fun canalRepetido_sale_una_vez() = runBlocking {
        collecting {
            collector.expect()
            collector.accept(entry("#Dup", 5, "a"))
            collector.accept(entry("#dup", 6, "b"))
            collector.accept(numeric("323"))
        }
        assertEquals(1, results.single().entries.size)
    }

    @Test
    fun tiempoAgotado_entregaLoRecogidoComoIncompleto() = runBlocking {
        collecting {
            collector.expect()
            collector.accept(entry("#a", 2, "uno"))
            collector.timeOut()
        }
        val r = results.single()
        assertEquals(ChannelListStatus.TIMED_OUT, r.status)
        assertEquals(1, r.entries.size)
        assertFalse(collector.isCollecting)
    }

    @Test
    fun tiempoAgotado_sinConsulta_noEmiteNada() = runBlocking {
        collecting { collector.timeOut() }
        assertTrue(results.isEmpty())
    }

    @Test
    fun tryAgain_y_demasiadosResultados_sonRechazo() = runBlocking {
        collecting {
            collector.expect()
            assertTrue(collector.accept(numeric("263", "LIST", trailing = "Please wait a while and try again.")))
            collector.expect()
            assertTrue(collector.accept(numeric("416", "LIST", trailing = "Too many lines in the output, restrict your query")))
        }
        assertEquals(listOf(ChannelListStatus.REFUSED, ChannelListStatus.REFUSED), results.map { it.status })
    }

    @Test
    fun rechazoDeOtroComando_noEsDeLaLista() {
        collector.expect()
        assertFalse(collector.accept(numeric("263", "WHOIS", trailing = "Please wait")))
        assertTrue(collector.isCollecting)
    }

    @Test
    fun cancelar_devuelveLasLineasAlServidor() {
        collector.expect()
        collector.cancel()
        assertFalse(collector.accept(entry("#a", 1, "x")))
    }

    @Test
    fun anunciarDeNuevo_reiniciaLaConsulta() = runBlocking {
        collecting {
            collector.expect()
            collector.accept(entry("#viejo", 1, "x"))
            collector.expect()
            collector.accept(entry("#nuevo", 2, "y"))
            collector.accept(numeric("323"))
        }
        assertEquals(listOf("#nuevo"), results.single().entries.map { it.name })
    }
}
