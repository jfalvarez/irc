package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmptyChatTest {

    private fun msg(type: UiMessageType) = UiChatMessage(fullText = type.name, type = type)

    @Test
    fun sinNingunMensaje_estaVacio() {
        assertTrue(EmptyChat.isEmpty(emptyList()))
    }

    @Test
    fun losAvisosDeSistemaDeAlAbrirNoCuentan() {
        assertTrue(EmptyChat.isEmpty(listOf(msg(UiMessageType.JOIN_PART_QUIT))))
        assertTrue(EmptyChat.isEmpty(listOf(msg(UiMessageType.SYSTEM_MESSAGE))))
        assertTrue(EmptyChat.isEmpty(listOf(msg(UiMessageType.MODE_CHANGE), msg(UiMessageType.SERVER_INFO))))
    }

    @Test
    fun losAnunciosNoCuentan() {
        assertTrue(EmptyChat.isEmpty(listOf(msg(UiMessageType.AD_MESSAGE), msg(UiMessageType.JOIN_PART_QUIT))))
    }

    @Test
    fun cualquierMensajeDeConversacion_loLlena() {
        listOf(
            UiMessageType.CHANNEL_MSG_RECEIVED, UiMessageType.CHANNEL_MSG_SENT,
            UiMessageType.PRIVATE_MSG_RECEIVED, UiMessageType.PRIVATE_MSG_SENT,
            UiMessageType.ACTION_MSG, UiMessageType.NOTICE
        ).forEach { type ->
            assertFalse(EmptyChat.isEmpty(listOf(msg(UiMessageType.JOIN_PART_QUIT), msg(type))), "$type debe llenar el chat")
        }
    }

    @Test
    fun conMuchasLineasDeSistema_yaNoSeDaPorVacio() {
        val few = List(EmptyChat.MAX_SYSTEM_LINES) { msg(UiMessageType.JOIN_PART_QUIT) }
        assertTrue(EmptyChat.isEmpty(few))
        assertFalse(EmptyChat.isEmpty(few + msg(UiMessageType.JOIN_PART_QUIT)))
    }
}
