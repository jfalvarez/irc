package com.jfaf.irc.domain.chat

import androidx.compose.ui.text.AnnotatedString
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatSearchTest {

    private fun msg(text: String, type: UiMessageType = UiMessageType.CHANNEL_MSG_RECEIVED, styled: String? = null) =
        TimelineItem.Message(
            UiChatMessage(fullText = text, annotatedString = styled?.let { AnnotatedString(it) }, type = type),
            showHeader = true,
            isMention = false
        )

    private val items = listOf(
        msg("<ana> hola mundo"),                              // 0
        TimelineItem.DateSeparator(20_000),                   // 1
        msg("<kiko> Música buena"),                           // 2
        msg("<ana> nada que ver"),                            // 3
        msg("<kiko> otro MUNDO más"),                         // 4
        TimelineItem.NewMessages,                             // 5
        msg("anuncio mundo", UiMessageType.AD_MESSAGE)        // 6
    )

    @Test
    fun devuelveLasPosicionesDelMasRecienteAlMasAntiguo() {
        assertEquals(listOf(4, 0), ChatSearch.matches(items, "mundo"))
    }

    @Test
    fun noDistingueMayusculasNiAcentos() {
        assertEquals(listOf(2), ChatSearch.matches(items, "musica"))
        assertEquals(listOf(2), ChatSearch.matches(items, "MÚSICA"))
        assertEquals(listOf(4, 0), ChatSearch.matches(items, "MuNdO"))
    }

    @Test
    fun laEnieEsOtraLetra() {
        val list = listOf(msg("el año pasado"), msg("el ano nuevo"))
        assertEquals(listOf(0), ChatSearch.matches(list, "año"))
        assertEquals(listOf(1), ChatSearch.matches(list, "ano"))
    }

    @Test
    fun tambienBuscaEnElNickDeQuienLoDijo() {
        assertEquals(listOf(3, 0), ChatSearch.matches(items, "ana"))
    }

    @Test
    fun buscaEnElTextoQueSeVe_noEnLosCodigosDeColor() {
        val coloured = listOf(msg("\u000304rojo\u0003 y verde", styled = "rojo y verde"))
        assertEquals(listOf(0), ChatSearch.matches(coloured, "rojo y"))
        assertEquals(emptyList(), ChatSearch.matches(coloured, "04"))
    }

    @Test
    fun ignoraAnunciosSeparadoresYConsultasVacias() {
        assertEquals(emptyList(), ChatSearch.matches(items, ""))
        assertEquals(emptyList(), ChatSearch.matches(items, "   "))
        assertEquals(listOf(4, 0), ChatSearch.matches(items, "  mundo  "), "Los espacios de los bordes no cuentan")
        assertEquals(emptyList(), ChatSearch.matches(items, "anuncio"), "Un anuncio no es un mensaje del chat")
        assertEquals(emptyList(), ChatSearch.matches(items, "zzz"))
    }

    @Test
    fun foldConservaLaLongitud() {
        assertEquals("musica", ChatSearch.fold("MÚSICA"))
        assertEquals("canon", ChatSearch.fold("cañón".replace('ñ', 'n')))
        val text = "ÁÉÍÓÚÜ áéíóú ñ"
        assertEquals(text.length, ChatSearch.fold(text).length)
    }

    @Test
    fun pasarDeUnResultadoAOtroDaLaVuelta() {
        assertEquals(1, ChatSearch.step(3, 0, towardsOlder = true))
        assertEquals(0, ChatSearch.step(3, 2, towardsOlder = true), "Tras el más antiguo, el más reciente")
        assertEquals(2, ChatSearch.step(3, 0, towardsOlder = false), "Antes del más reciente, el más antiguo")
        assertEquals(0, ChatSearch.step(0, 0, towardsOlder = true))
        assertEquals(0, ChatSearch.step(1, 0, towardsOlder = false))
        assertEquals(0, ChatSearch.step(3, 7, towardsOlder = true), "Una posición fuera de rango se acota")
    }
}
