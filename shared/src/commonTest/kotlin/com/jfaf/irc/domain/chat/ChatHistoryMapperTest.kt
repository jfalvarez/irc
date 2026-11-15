package com.jfaf.irc.domain.chat

import com.jfaf.irc.data.history.StoredMessage
import com.jfaf.irc.domain.chat.model.MediaTypeEnum
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class ChatHistoryMapperTest {

    @Test
    fun ida_y_vuelta_conservaLosCampos() {
        val original = UiChatMessage(
            fullText = "<ana> hola", type = UiMessageType.CHANNEL_MSG_RECEIVED, sender = "ana",
            isOwnMessage = false, mediaUrl = "https://x.org/a.gif", mediaType = MediaTypeEnum.IMAGE
        )
        val restored = original.toStored().toUi()
        assertEquals(original.fullText, restored.fullText)
        assertEquals(original.type, restored.type)
        assertEquals(original.sender, restored.sender)
        assertEquals(original.mediaUrl, restored.mediaUrl)
        assertEquals(original.mediaType, restored.mediaType)
        assertEquals("<ana> hola", restored.annotatedString?.text)
    }

    @Test
    fun valoresDesconocidosTienenFallback() {
        val restored = StoredMessage("x", "TIPO_QUE_YA_NO_EXISTE", null, false, null, "OTRO").toUi()
        assertEquals(UiMessageType.OTHER_COMMAND, restored.type)
        assertEquals(MediaTypeEnum.NONE, restored.mediaType)
    }

    @Test
    fun losAnunciosNoSonPersistibles() {
        assertFalse(UiChatMessage(fullText = "", type = UiMessageType.AD_MESSAGE).isPersistable())
        assertTrue(UiChatMessage(fullText = "a", type = UiMessageType.NOTICE).isPersistable())
    }

    @Test
    fun ida_y_vuelta_conservaLaHoraYLaPresencia() {
        val original = UiChatMessage(
            fullText = "ana entra", type = UiMessageType.JOIN_PART_QUIT, sender = "ana",
            timestamp = 1_700_000_000_000L, presence = com.jfaf.irc.domain.chat.model.PresenceEvent.QUIT
        )
        val restored = original.toStored().toUi()
        assertEquals(1_700_000_000_000L, restored.timestamp)
        assertEquals(com.jfaf.irc.domain.chat.model.PresenceEvent.QUIT, restored.presence)
    }

    @Test
    fun loGuardadoAntesNoTieneHoraNiPresencia() {
        val restored = StoredMessage("x", "NOTICE", null, false, null, "NONE").toUi()
        assertEquals(null, restored.timestamp)
        assertEquals(null, restored.presence)
    }

    @Test
    fun presenciaDesconocidaSeIgnora() {
        val restored = StoredMessage("x", "JOIN_PART_QUIT", null, false, null, "NONE", 5L, "RARO").toUi()
        assertEquals(null, restored.presence)
        assertEquals(5L, restored.timestamp)
    }
}
