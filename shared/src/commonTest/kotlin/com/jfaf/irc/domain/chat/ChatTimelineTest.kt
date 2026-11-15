package com.jfaf.irc.domain.chat

import androidx.compose.ui.text.AnnotatedString
import com.jfaf.irc.domain.chat.model.PresenceEvent
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatTimelineTest {
    private val hour = 3_600_000L
    private val day = 24 * hour

    // Día "UTC" para los tests
    private val dayOf: (Long) -> Long = { it / day }

    private fun say(
        nick: String,
        text: String,
        type: UiMessageType = UiMessageType.CHANNEL_MSG_RECEIVED,
        at: Long? = null
    ) = UiChatMessage(
        fullText = "<$nick> $text", annotatedString = AnnotatedString("<$nick> $text"),
        type = type, sender = nick, timestamp = at
    )

    private fun presence(nick: String, event: PresenceEvent, at: Long? = null) = UiChatMessage(
        fullText = "$nick $event", type = UiMessageType.JOIN_PART_QUIT, sender = nick, presence = event, timestamp = at
    )

    private fun system(text: String) = UiChatMessage(fullText = text, type = UiMessageType.SYSTEM_MESSAGE)

    private fun build(messages: List<UiChatMessage>, newFromEnd: Int = 0, ownNick: String? = null) =
        ChatTimeline.build(messages, newFromEnd, ownNick, dayOf)

    private fun headers(items: List<TimelineItem>) =
        items.filterIsInstance<TimelineItem.Message>().map { it.showHeader }

    // --- UI-10: cabeceras ---

    @Test
    fun seguidosDelMismoNickCompartenCabecera() {
        val items = build(listOf(say("ana", "1"), say("ana", "2"), say("kiko", "3"), say("ana", "4")))
        assertEquals(listOf(true, false, true, true), headers(items))
    }

    @Test
    fun noDistingueMayusculasNiPrefijo() {
        val items = build(listOf(say("Ana", "1"), say("ana", "2"), say("@ana", "3")))
        assertEquals(listOf(true, false, false), headers(items))
    }

    @Test
    fun unAvisoIntermedioCortaLaTanda() {
        val items = build(listOf(say("ana", "1"), system("aviso"), say("ana", "2")))
        assertEquals(listOf(true, false, true), headers(items))
    }

    @Test
    fun losAvisosNoLlevanCabecera() {
        val items = build(listOf(system("a")))
        assertFalse((items.single() as TimelineItem.Message).showHeader)
    }

    // --- UI-5: menciones ---

    @Test
    fun marcaLasMencionesDeCanalRecibidas() {
        val items = build(
            listOf(say("kiko", "hola ana, ven"), say("kiko", "banana"), say("ana", "ana", UiMessageType.CHANNEL_MSG_SENT)),
            ownNick = "Ana"
        ).filterIsInstance<TimelineItem.Message>()
        assertEquals(listOf(true, false, false), items.map { it.isMention })
    }

    @Test
    fun losPrivadosNoSeMarcanComoMencion() {
        val items = build(listOf(say("kiko", "ana", UiMessageType.PRIVATE_MSG_RECEIVED)), ownNick = "ana")
        assertFalse((items.single() as TimelineItem.Message).isMention)
    }

    @Test
    fun sinNickNoHayMenciones() {
        val items = build(listOf(say("kiko", "hola ana")), ownNick = null)
        assertFalse((items.single() as TimelineItem.Message).isMention)
    }

    // --- UI-6: entradas y salidas ---

    @Test
    fun agrupaEntradasYSalidasSeguidas() {
        val items = build(listOf(presence("ana", PresenceEvent.JOIN), presence("kiko", PresenceEvent.JOIN), presence("luis", PresenceEvent.QUIT)))
        val group = assertIs<TimelineItem.PresenceGroup>(items.single())
        assertEquals(3, group.messages.size)
    }

    @Test
    fun unaSolaEntradaSigueSiendoUnaLinea() {
        val items = build(listOf(presence("ana", PresenceEvent.JOIN)))
        assertIs<TimelineItem.Message>(items.single())
    }

    @Test
    fun unMensajeCortaElGrupo() {
        val items = build(
            listOf(
                presence("a", PresenceEvent.JOIN), presence("b", PresenceEvent.JOIN),
                say("kiko", "hola"),
                presence("c", PresenceEvent.PART)
            )
        )
        assertEquals(3, items.size)
        assertIs<TimelineItem.PresenceGroup>(items[0])
        assertIs<TimelineItem.Message>(items[1])
        assertIs<TimelineItem.Message>(items[2]) // un solo aviso: línea normal
    }

    @Test
    fun losAvisosSinTipoDePresenciaNoSeAgrupan() {
        val kick = UiChatMessage(fullText = "x echó a y", type = UiMessageType.JOIN_PART_QUIT)
        val items = build(listOf(kick, kick))
        assertEquals(2, items.size)
        assertTrue(items.all { it is TimelineItem.Message })
    }

    // --- UI-7: separadores ---

    @Test
    fun poneUnSeparadorDeFechaAlCambiarDeDia() {
        val items = build(
            listOf(say("ana", "1", at = 1 * day + hour), say("ana", "2", at = 1 * day + 2 * hour), say("ana", "3", at = 2 * day + hour))
        )
        assertEquals(
            listOf("date1", "msg", "msg", "date2", "msg"),
            items.map {
                when (it) {
                    is TimelineItem.DateSeparator -> "date${it.epochDay}"
                    is TimelineItem.Message -> "msg"
                    else -> "otro"
                }
            }
        )
    }

    @Test
    fun elCambioDeDiaCortaLaTandaDelMismoNick() {
        val items = build(listOf(say("ana", "1", at = day + hour), say("ana", "2", at = 2 * day + hour)))
        assertEquals(listOf(true, true), headers(items))
    }

    @Test
    fun losMensajesSinHoraNoTienenSeparador() {
        val items = build(listOf(say("ana", "1"), say("ana", "2")))
        assertTrue(items.none { it is TimelineItem.DateSeparator })
    }

    @Test
    fun elSeparadorAparecePorPrimeraVezEnElPrimerMensajeConHora() {
        val items = build(listOf(say("ana", "viejo"), say("ana", "nuevo", at = 3 * day)))
        assertIs<TimelineItem.Message>(items[0])
        assertIs<TimelineItem.DateSeparator>(items[1])
    }

    @Test
    fun separadorDeNuevosMensajes() {
        val items = build(listOf(say("ana", "1"), say("kiko", "2"), say("kiko", "3")), newFromEnd = 2)
        assertEquals(4, items.size)
        assertIs<TimelineItem.NewMessages>(items[1])
        // la tanda de kiko se parte en el separador: el primero de los nuevos lleva cabecera
        assertEquals(listOf(true, true, false), headers(items))
    }

    @Test
    fun sinNuevosOFueraDeRangoNoHaySeparador() {
        val messages = listOf(say("ana", "1"), say("ana", "2"))
        assertTrue(build(messages, newFromEnd = 0).none { it is TimelineItem.NewMessages })
        assertTrue(build(messages, newFromEnd = 5).none { it is TimelineItem.NewMessages })
        assertTrue(build(messages, newFromEnd = -1).none { it is TimelineItem.NewMessages })
    }

    @Test
    fun todosNuevosPoneElSeparadorAlPrincipio() {
        val items = build(listOf(say("ana", "1"), say("ana", "2")), newFromEnd = 2)
        assertIs<TimelineItem.NewMessages>(items.first())
    }

    @Test
    fun listaVacia() {
        assertEquals(emptyList(), build(emptyList()))
    }

    // --- Claves estables (animaciones)

    @Test
    fun clavesUnicasAunConMensajesIguales() {
        val same = say("ana", "hola", at = day)
        val items = build(listOf(same, same, same, say("ana", "otro", at = day)))
        val keys = items.stableKeys()
        assertEquals(keys.size, keys.toSet().size, keys.toString())
    }

    @Test
    fun lasClavesNoCambianAlLlegarUnMensajeNuevo() {
        val a = say("ana", "uno", at = day + hour)
        val b = say("kiko", "dos", at = day + 2 * hour)
        val before = build(listOf(a, b)).stableKeys()
        val after = build(listOf(a, b, say("luis", "tres", at = day + 3 * hour))).stableKeys()
        assertEquals(before, after.take(before.size))
    }

    @Test
    fun clavesDeLosSeparadoresYGrupos() {
        val items = build(
            listOf(say("ana", "1", at = day), presence("a", PresenceEvent.JOIN, day), presence("b", PresenceEvent.JOIN, day), say("kiko", "2", at = 2 * day)),
            newFromEnd = 1
        )
        val keys = items.stableKeys()
        assertTrue("new" in keys)
        assertTrue(keys.any { it.startsWith("d-") })
        assertTrue(keys.any { it.startsWith("g-") })
        assertEquals(keys.size, keys.toSet().size)
    }
}
