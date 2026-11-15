package com.jfaf.irc.domain.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageGroupingTest {
    private fun say(nick: String, text: String, type: UiMessageType = UiMessageType.CHANNEL_MSG_RECEIVED) =
        UiChatMessage(fullText = "<$nick> $text", annotatedString = AnnotatedString("<$nick> $text"), type = type, sender = nick)

    private fun system(text: String, type: UiMessageType = UiMessageType.JOIN_PART_QUIT) =
        UiChatMessage(fullText = text, annotatedString = AnnotatedString(text), type = type)

    @Test
    fun separaElNickDelCuerpo() {
        val speech = MessageGrouping.speech(say("ana", "hola > mundo"))!!
        assertEquals("ana", speech.nick)
        assertEquals("hola > mundo", speech.body.text)
    }

    @Test
    fun conservaLosEstilosDelCuerpo() {
        val styled = buildAnnotatedString {
            append("<ana> ")
            withStyle(SpanStyle(color = Color.Red)) { append("rojo") }
        }
        val message = UiChatMessage(fullText = "<ana> rojo", annotatedString = styled, type = UiMessageType.CHANNEL_MSG_RECEIVED)
        val body = MessageGrouping.speech(message)!!.body
        assertEquals("rojo", body.text)
        assertEquals(Color.Red, body.spanStyles.single().item.color)
        assertEquals(0, body.spanStyles.single().start)
    }

    @Test
    fun sinAnnotatedStringUsaElTextoCompleto() {
        val message = UiChatMessage(fullText = "<ana> hola", type = UiMessageType.PRIVATE_MSG_RECEIVED)
        assertEquals("hola", MessageGrouping.speech(message)!!.body.text)
    }

    @Test
    fun noEsConversacion() {
        assertNull(MessageGrouping.speech(system("ana entra")))
        assertNull(MessageGrouping.speech(system("* ana baila", UiMessageType.ACTION_MSG)))
        assertNull(MessageGrouping.speech(UiChatMessage(fullText = "sin formato", type = UiMessageType.CHANNEL_MSG_RECEIVED)))
        assertNull(MessageGrouping.speech(UiChatMessage(fullText = "<a b> x", type = UiMessageType.CHANNEL_MSG_RECEIVED)))
    }
}
