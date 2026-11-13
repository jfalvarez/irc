package com.jfaf.irc.ui.viewmodels

import com.jfaf.irc.ui.model.UiChatMessage
import com.jfaf.irc.ui.model.UiMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStateManagerTest {

    private val server = ChatStateManager.SERVER_TARGET_ID
    private val state = ChatStateManager()

    private fun msg(text: String) = UiChatMessage(fullText = text, type = UiMessageType.NOTICE)

    @Test
    fun mensajeDeSistema_seAnadeAlTarget() {
        state.addSystemMessageToTarget("#chat", "hola")
        assertEquals("hola", state.allMessages.value.getValue("#chat").single().fullText)
    }

    @Test
    fun cadaTargetConservaComoMaximo150Mensajes() {
        repeat(200) { state.addLocalUiMessageToTarget("#chat", msg("m$it")) }
        val list = state.allMessages.value.getValue("#chat")
        assertEquals(150, list.size)
        assertEquals("m199", list.last().fullText)
        assertEquals("m50", list.first().fullText)
    }

    @Test
    fun abrirUnPrivado_creaPestanaLaActivaYServidorSiempreVaPrimero() {
        state.openPrivateMessageTarget("Ana", "Yo")
        assertEquals(listOf("Ana"), state.chatTargets.value - server)
        assertEquals("Ana", state.activeTarget.value)
        assertEquals(server, state.chatTargets.value.first())
    }

    @Test
    fun privadoInvalido_seIgnora() {
        state.openPrivateMessageTarget("#canal", "Yo")
        state.openPrivateMessageTarget("yo", "Yo")
        state.openPrivateMessageTarget("", "Yo")
        assertFalse(state.chatTargets.value.any { it == "#canal" || it.equals("yo", true) || it.isEmpty() })
    }

    @Test
    fun cerrarUnPrivadoActivo_pasaALaPrimeraPestanaYBorraSuHistorial() {
        state.openPrivateMessageTarget("Ana", "Yo")
        state.addSystemMessageToTarget("Ana", "x")
        val next = state.closeTarget("Ana", state.activeTarget.value, "Yo")
        assertEquals(server, next)
        assertFalse("Ana" in state.chatTargets.value)
        assertNull(state.allMessages.value["Ana"])
    }

    @Test
    fun laPestanaServidorNoSePuedeCerrar() {
        assertNull(state.closeTarget(server, server, "Yo"))
    }

    @Test
    fun activarUnTargetInexistente_vuelveAlPrimero() {
        state.setActiveTarget("#noexiste", "Yo")
        assertEquals(server, state.activeTarget.value)
    }

    @Test
    fun clearMessages_vaciaElTarget() {
        state.addSystemMessageToTarget("#chat", "x")
        state.clearMessagesForTarget("#chat")
        assertTrue(state.allMessages.value.getValue("#chat").isEmpty())
    }

    @Test
    fun usuariosDeCanal_seOrdenanOpsVocesYResto() {
        state.updateUsersForChannel("#chat", listOf("zeta", "+voz", "@op", "Ana"))
        assertEquals(listOf("@op", "+voz", "Ana", "zeta"), state.usersInChannel.value.getValue("#chat"))
    }

    // --- Restauración y reseteos (#19)
    @Test
    fun restore_cargaPestanasHistorialYActiva() {
        state.restore(listOf("#chat", "Ana"), "Ana", mapOf("#chat" to listOf(msg("a")), "Ana" to listOf(msg("b"))))
        assertEquals(listOf(server, "#chat", "Ana"), state.chatTargets.value)
        assertEquals("Ana", state.activeTarget.value)
        assertEquals("a", state.allMessages.value.getValue("#chat").single().fullText)
    }

    @Test
    fun restore_conActivaInvalidaUsaServidor() {
        state.restore(listOf("#chat"), "#borrado", emptyMap())
        assertEquals(server, state.activeTarget.value)
    }

    @Test
    fun restore_noPisaUnEstadoYaEnUso() {
        state.addSystemMessageToTarget("#chat", "ya hay algo")
        state.addChannelTarget("#chat")
        state.restore(listOf("#otro"), null, mapOf("#otro" to listOf(msg("x"))))
        assertFalse("#otro" in state.chatTargets.value)
    }

    @Test
    fun resetPorConexion_conservaPestanasEHistorialPeroLimpiaServidorYNoLeidos() {
        state.restore(listOf("#chat"), "#chat", mapOf("#chat" to listOf(msg("a"))))
        state.addSystemMessageToTarget(server, "MOTD")
        state.updateUsersForChannel("#chat", listOf("Ana"))

        state.resetStateForConnection()

        assertEquals(listOf(server, "#chat"), state.chatTargets.value)
        assertEquals("a", state.allMessages.value.getValue("#chat").single().fullText)
        assertTrue(state.allMessages.value.getValue(server).isEmpty())
        assertTrue(state.usersInChannel.value.isEmpty())
        assertEquals("#chat", state.activeTarget.value)
    }

    @Test
    fun resetPorDesconexion_conservaPestanasEHistorial() {
        state.restore(listOf("#chat"), "#chat", mapOf("#chat" to listOf(msg("a"))))
        state.updateUsersForChannel("#chat", listOf("Ana"))

        state.resetStateForDisconnection()

        assertEquals(listOf(server, "#chat"), state.chatTargets.value)
        assertEquals("a", state.allMessages.value.getValue("#chat").single().fullText)
        assertTrue(state.usersInChannel.value.isEmpty())
    }
}
