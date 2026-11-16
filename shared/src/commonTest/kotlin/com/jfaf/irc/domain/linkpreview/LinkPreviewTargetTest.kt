package com.jfaf.irc.domain.linkpreview

import com.jfaf.irc.domain.chat.model.MediaTypeEnum
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkPreviewTargetTest {

    private fun msg(text: String, type: UiMessageType = UiMessageType.CHANNEL_MSG_RECEIVED, mediaUrl: String? = null) =
        UiChatMessage(fullText = text, type = type, mediaUrl = mediaUrl, mediaType = if (mediaUrl != null) MediaTypeEnum.IMAGE else MediaTypeEnum.NONE)

    @Test
    fun elPrimerEnlaceDelCuerpo() {
        assertEquals("https://example.com/a", LinkPreviewTarget.of(msg("<ana> mira https://example.com/a y https://otra.com")))
    }

    @Test
    fun enlaceSinEsquemaSeCompletaConHttps() {
        assertEquals("https://www.example.com", LinkPreviewTarget.of(msg("<ana> www.example.com")))
    }

    @Test
    fun noMiraElNickDelAutor() {
        assertNull(LinkPreviewTarget.of(msg("<ana.example.com> hola")))
    }

    @Test
    fun saltaLosEnlacesAWebsPrivadas() {
        assertEquals("https://example.com", LinkPreviewTarget.of(msg("<ana> http://192.168.1.1/admin y https://example.com")))
        assertNull(LinkPreviewTarget.of(msg("<ana> http://localhost:8080/x")))
    }

    @Test
    fun siElMensajeYaLlevaImagenOVideo_noSePrevisualizaElEnlace() {
        assertNull(LinkPreviewTarget.of(msg("<ana> https://i.example.com/a.png", mediaUrl = "https://i.example.com/a.png")))
    }

    @Test
    fun soloMensajesDeConversacion() {
        assertNull(LinkPreviewTarget.of(msg("* ana ha entrado https://example.com", UiMessageType.JOIN_PART_QUIT)))
        assertNull(LinkPreviewTarget.of(msg("https://example.com", UiMessageType.SERVER_INFO)))
        assertEquals("https://example.com", LinkPreviewTarget.of(msg("<yo> https://example.com", UiMessageType.CHANNEL_MSG_SENT)))
        assertEquals("https://example.com", LinkPreviewTarget.of(msg("<kiko> https://example.com", UiMessageType.PRIVATE_MSG_RECEIVED)))
    }

    @Test
    fun sinEnlace_nada() {
        assertNull(LinkPreviewTarget.of(msg("<ana> hola a todos")))
    }
}
