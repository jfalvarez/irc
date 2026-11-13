package com.jfaf.irc.domain.chat

import com.jfaf.irc.data.model.IrcLineParser
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import com.jfaf.irc.domain.config.AppConfig
import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

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

    // --- Privados sin leer (#28): se recibe una línea real, la procesa el IrcMessageHandler y se aplica al estado
    private val handler = IrcMessageHandler(
        object : StringProvider {
            override fun get(msg: Msg, vararg args: Any) = msg.name
        },
        object : AppConfig {
            override val imageFilterKeywords = emptySet<String>()
            override val googleSignInClientId = ""
        }
    )

    private fun receive(line: String, own: String = "Yo") {
        val snapshot = ChatUiSnapshot(
            currentNickname = own,
            activeTarget = state.activeTarget.value,
            allMessages = state.allMessages.value,
            chatTargets = state.chatTargets.value,
            unreadTargets = state.unreadTargets.value,
            usersInChannel = state.usersInChannel.value
        )
        state.updateStateFromHandlerResult(handler.processMessage(snapshot, IrcLineParser.parse(line)!!)) { own }
    }

    private fun pm(from: String, text: String = "hola") = receive(":$from!u@h PRIVMSG Yo :$text")

    @Test
    fun privadosEnPestanaNoActiva_sumanAlContador() {
        pm("Ana")
        pm("Ana")
        pm("Luis")
        assertEquals(mapOf("Ana" to 2, "Luis" to 1), state.unreadPmCounts.value)
        assertTrue("Ana" in state.unreadTargets.value)
    }

    @Test
    fun privadoEnLaPestanaActiva_noCuenta() {
        state.openPrivateMessageTarget("Ana", "Yo")
        pm("Ana")
        assertTrue(state.unreadPmCounts.value.isEmpty())
        assertFalse("Ana" in state.unreadTargets.value)
    }

    @Test
    fun mensajeDeCanal_noSumaAlContadorDePrivados() {
        state.addChannelTarget("#chat")
        receive(":Ana!u@h PRIVMSG #chat :hola a todos")
        assertTrue(state.unreadPmCounts.value.isEmpty())
        assertTrue("#chat" in state.unreadTargets.value)
    }

    @Test
    fun mensajePropio_noSuma() {
        state.addChannelTarget("#chat")
        receive(":Yo!u@h PRIVMSG Ana :hola")
        assertTrue(state.unreadPmCounts.value.isEmpty())
    }

    @Test
    fun activarLaConversacion_poneSuContadorACero() {
        pm("Ana")
        pm("Luis")
        state.setActiveTarget("Ana", "Yo")
        assertEquals(mapOf("Luis" to 1), state.unreadPmCounts.value)
    }

    @Test
    fun abrirLaConversacion_poneSuContadorACero() {
        pm("Ana")
        state.openPrivateMessageTarget("Ana", "Yo")
        assertTrue(state.unreadPmCounts.value.isEmpty())
    }

    @Test
    fun despuesDeLeer_losNuevosPrivadosEmpiezanDeCero() {
        pm("Ana")
        pm("Ana")
        state.setActiveTarget("Ana", "Yo")
        state.setActiveTarget(server, "Yo")
        pm("Ana")
        assertEquals(mapOf("Ana" to 1), state.unreadPmCounts.value)
    }

    @Test
    fun cerrarLaConversacion_borraSuContador() {
        pm("Ana")
        state.closeTarget("Ana", state.activeTarget.value, "Yo")
        assertTrue(state.unreadPmCounts.value.isEmpty())
    }

    @Test
    fun resetPorConexionYDesconexion_borranLosContadores() {
        pm("Ana")
        state.resetStateForConnection()
        assertTrue(state.unreadPmCounts.value.isEmpty())

        pm("Ana")
        state.resetStateForDisconnection()
        assertTrue(state.unreadPmCounts.value.isEmpty())
    }
}
