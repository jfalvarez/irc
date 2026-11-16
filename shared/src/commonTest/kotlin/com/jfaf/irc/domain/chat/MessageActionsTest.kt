package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageActionsTest {

    private fun msg(text: String, type: UiMessageType) = UiChatMessage(fullText = text, type = type)
    private val channel = msg("<ana> hola a todos", UiMessageType.CHANNEL_MSG_RECEIVED)
    private val own = msg("<yo> buenas", UiMessageType.CHANNEL_MSG_SENT)
    private val privado = msg("<kiko> psst", UiMessageType.PRIVATE_MSG_RECEIVED)
    private val notice = msg("Ana entra", UiMessageType.JOIN_PART_QUIT)
    private val ad = msg("Anuncio", UiMessageType.AD_MESSAGE)

    @Test
    fun copiaElCuerpoSinElNick_yElTextoEnteroEnLosAvisos() {
        assertEquals("hola a todos", MessageActions.copyText(channel))
        assertEquals("Ana entra", MessageActions.copyText(notice))
    }

    @Test
    fun losAnunciosNoSeCopian() {
        assertFalse(MessageActions.canCopy(ad))
        assertTrue(MessageActions.canCopy(notice))
    }

    @Test
    fun mencionarSoloEnCanalYAOtros() {
        assertTrue(MessageActions.canMention(channel))
        assertFalse(MessageActions.canMention(own))
        assertFalse(MessageActions.canMention(privado))
        assertFalse(MessageActions.canMention(notice))
        assertNull(MessageActions.mentionInsertion(own))
    }

    @Test
    fun citarLoDeLosDemas_enCanalesYPrivados() {
        assertTrue(MessageActions.canQuote(channel))
        assertTrue(MessageActions.canQuote(privado))
        assertFalse(MessageActions.canQuote(own))
        assertFalse(MessageActions.canQuote(notice))
    }

    @Test
    fun laCitaDeUnPrivadoNoNombraAlAutor() {
        assertEquals(InputInsertion.Quote(null, "psst"), MessageActions.quoteInsertion(privado))
        assertEquals(InputInsertion.Quote("ana", "hola a todos"), MessageActions.quoteInsertion(channel))
    }

    @Test
    fun mencionConCampoVacio_poneDosPuntos() {
        assertEquals("ana: ", MessageActions.render(InputInsertion.Mention("ana"), "", isFieldEmpty = true))
    }

    @Test
    fun mencionConTexto_poneEspacioYSeparaSiHaceFalta() {
        assertEquals("ana ", MessageActions.render(InputInsertion.Mention("ana"), "hola ", isFieldEmpty = false))
        assertEquals(" ana ", MessageActions.render(InputInsertion.Mention("ana"), "hola", isFieldEmpty = false))
    }

    @Test
    fun citaConCampoVacio_nombraAlAutorEnCanales() {
        assertEquals("ana: «hola» ", MessageActions.render(InputInsertion.Quote("ana", "hola"), "", true))
        assertEquals("«hola» ", MessageActions.render(InputInsertion.Quote(null, "hola"), "", true))
    }

    @Test
    fun citaConTexto_noNombraAlAutor() {
        assertEquals("«hola» ", MessageActions.render(InputInsertion.Quote("ana", "hola"), "ok ", false))
    }

    @Test
    fun citaLarga_seAcortaEnUnaLinea() {
        val long = "palabra ".repeat(40)
        val shortened = MessageActions.shorten("  $long\n y más ")
        assertTrue(shortened.length <= MessageActions.MAX_QUOTE_LENGTH)
        assertTrue(shortened.endsWith("…"))
        assertFalse(shortened.contains("\n"))
        assertEquals("a b c", MessageActions.shorten("a\n  b\t c"))
    }
}
