package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hora de llegada, separador de «nuevos mensajes» (UI-7) y menciones sin leer (UI-5) en `ChatStateManager`. */
class ChatStateManagerTimelineTest {

    private var now = 1_000L
    private val state = ChatStateManager(clock = { now })

    private fun say(nick: String, text: String) = UiChatMessage(
        fullText = "<$nick> $text", type = UiMessageType.CHANNEL_MSG_RECEIVED, sender = nick
    )

    /** Llega [message] a [target], como lo entrega el manejador de mensajes (con la lista completa ya actualizada). */
    private fun receive(target: String, message: UiChatMessage, unread: Set<String>) {
        val all = state.allMessages.value
        state.updateStateFromHandlerResult(
            ChatUpdateResult(
                newCurrentNickname = "yo",
                newActiveTarget = state.activeTarget.value,
                newAllMessages = all + (target to (all[target].orEmpty() + message)),
                newChatTargets = state.chatTargets.value,
                newUnreadTargets = unread,
                uiMessageToAdd = message,
                targetForUiMessage = target
            ),
            currentOwnNickProvider = { "yo" }
        )
    }

    private fun twoTabs() {
        state.restore(listOf("#a", "#b"), "#a", mapOf("#a" to listOf(UiChatMessage(fullText = "viejo", type = UiMessageType.NOTICE))))
    }

    @Test
    fun ponelaHoraDeLlegadaALosMensajesLocales() {
        state.addSystemMessageToTarget("#a", "hola")
        assertEquals(1_000L, state.allMessages.value.getValue("#a").single().timestamp)
    }

    @Test
    fun noPisaUnaHoraYaConocida() {
        state.addLocalUiMessageToTarget("#a", UiChatMessage(fullText = "x", type = UiMessageType.NOTICE, timestamp = 5L))
        assertEquals(5L, state.allMessages.value.getValue("#a").single().timestamp)
    }

    @Test
    fun ponelaHoraSoloALoNuevoDeLaListaDelManejador() {
        twoTabs()
        now = 2_000L
        receive("#a", say("kiko", "hola"), emptySet())
        val list = state.allMessages.value.getValue("#a")
        assertNull(list[0].timestamp) // el del historial sigue sin hora
        assertEquals(2_000L, list[1].timestamp)
    }

    @Test
    fun elSeparadorNaceConElPrimerMensajeSinLeerYCreceConLosSiguientes() {
        twoTabs()
        receive("#b", say("kiko", "uno"), setOf("#b"))
        assertEquals(1, state.newMessageAnchors.value["#b"])
        receive("#b", say("kiko", "dos"), setOf("#b"))
        assertEquals(2, state.newMessageAnchors.value["#b"])
    }

    @Test
    fun sinNoLeidoNoHaySeparador() {
        twoTabs()
        receive("#a", say("kiko", "uno"), emptySet())
        assertTrue(state.newMessageAnchors.value.isEmpty())
    }

    @Test
    fun alAbrirLaPestanaElSeparadorSeConservaYSigueCreciendo() {
        twoTabs()
        receive("#b", say("kiko", "uno"), setOf("#b"))
        receive("#b", say("kiko", "dos"), setOf("#b"))
        state.setActiveTarget("#b", "yo")
        assertEquals(2, state.newMessageAnchors.value["#b"])
        receive("#b", say("kiko", "tres"), emptySet())
        assertEquals(3, state.newMessageAnchors.value["#b"])
    }

    @Test
    fun alSalirDeLaPestanaSeBorraElSeparador() {
        twoTabs()
        receive("#b", say("kiko", "uno"), setOf("#b"))
        state.setActiveTarget("#b", "yo")
        state.setActiveTarget("#a", "yo")
        assertTrue(state.newMessageAnchors.value.isEmpty())
    }

    @Test
    fun cerrarUnPrivadoBorraSuSeparador() {
        state.openPrivateMessageTarget("ana", "yo")
        state.setActiveTarget(ChatStateManager.SERVER_TARGET_ID, "yo")
        val pm = UiChatMessage(fullText = "<ana> hola", type = UiMessageType.PRIVATE_MSG_RECEIVED, sender = "ana")
        receive("ana", pm, setOf("ana"))
        assertEquals(1, state.newMessageAnchors.value["ana"])
        state.closeTarget("ana", ChatStateManager.SERVER_TARGET_ID, "yo")
        assertTrue(state.newMessageAnchors.value.isEmpty())
    }

    @Test
    fun cuentaLasMencionesDeCanalSinLeer() {
        twoTabs()
        receive("#b", say("kiko", "hola yo, ven"), setOf("#b"))
        receive("#b", say("kiko", "otra cosa"), setOf("#b"))
        receive("#b", say("luis", "YO tambien"), setOf("#b"))
        assertEquals(2, state.unreadMentionCounts.value["#b"])
    }

    @Test
    fun lasMencionesSeBorranAlLeerElCanal() {
        twoTabs()
        receive("#b", say("kiko", "hola yo"), setOf("#b"))
        state.setActiveTarget("#b", "yo")
        assertTrue(state.unreadMentionCounts.value.isEmpty())
    }

    @Test
    fun unaMencionEnElCanalActivoNoSuma() {
        twoTabs()
        receive("#a", say("kiko", "hola yo"), emptySet())
        assertTrue(state.unreadMentionCounts.value.isEmpty())
    }

    @Test
    fun noEsMencionSiElNickFormaPartedeOtraPalabra() {
        twoTabs()
        receive("#b", say("kiko", "yoyo y yogurt"), setOf("#b"))
        assertTrue(state.unreadMentionCounts.value.isEmpty())
    }
}
