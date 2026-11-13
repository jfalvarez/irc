package com.jfaf.irc.domain.chat.command

import com.jfaf.irc.domain.IrcSender
import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.domain.chat.model.UiMessageType
import com.jfaf.irc.domain.chat.ChatStateManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class CommandProcessorTest {

    private class FakeSender : IrcSender {
        val messages = mutableListOf<Pair<String, String>>()
        val raw = mutableListOf<String>()
        override fun sendMessage(target: String, message: String) { messages += target to message }
        override fun sendRawCommand(command: String, isSilent: Boolean) { raw += command }
    }

    private val fakeStrings = object : StringProvider {
        override fun get(msg: Msg, vararg args: Any) = (listOf(msg.name) + args.map { it.toString() }).joinToString("|")
    }

    private val sender = FakeSender()
    private val state = ChatStateManager()
    private val processor = CommandProcessor(sender, state, fakeStrings)

    private fun run(line: String, active: String? = "#chat", nick: String = "Yo") = processor.process(line, active, nick)

    @Test
    fun comandoVacio_esInvalido() {
        assertEquals(CommandResult.InvalidArguments("CMD_INVALID"), run("/"))
    }

    @Test
    fun comandoDesconocido() {
        assertEquals(CommandResult.UnknownCommand("xyz"), run("/xyz"))
    }

    @Test
    fun comandosSeInterpretanSinImportarMayusculas() {
        assertEquals(CommandResult.Quit("adios"), run("/QUIT adios"))
    }

    // --- /me
    @Test
    fun me_sinArgumentos_muestraUso() {
        assertEquals(CommandResult.InvalidArguments("CMD_ME_USAGE"), run("/me"))
    }

    @Test
    fun me_enLaPestanaServidor_esUnError() {
        val r = run("/me baila", active = ChatStateManager.SERVER_TARGET_ID)
        assertEquals(CommandResult.ShowSystemMessage(ChatStateManager.SERVER_TARGET_ID, "CMD_ME_WRONG_CONTEXT", true), r)
        assertTrue(sender.messages.isEmpty())
    }

    @Test
    fun me_enUnCanal_enviaCtcpActionYAnadeMensajeLocal() {
        assertEquals(CommandResult.Handled, run("/me baila"))
        assertEquals("#chat" to "\u0001ACTION baila\u0001", sender.messages.single())
        val local = state.allMessages.value.getValue("#chat").single()
        assertEquals(UiMessageType.ACTION_MSG, local.type)
        assertEquals("* Yo baila", local.fullText)
        assertTrue(local.isOwnMessage)
    }

    // --- /nick, /join, /part, /away
    @Test
    fun nick_sinArgumentos_muestraUso_yConArgumentoEnviaNICK() {
        assertEquals(CommandResult.InvalidArguments("CMD_NICK_USAGE"), run("/nick"))
        assertEquals(CommandResult.Handled, run("/nick Nuevo"))
        assertEquals(listOf("NICK Nuevo"), sender.raw)
    }

    @Test
    fun join_conYSinClave() {
        assertEquals(CommandResult.InvalidArguments("CMD_JOIN_USAGE"), run("/join"))
        assertEquals(CommandResult.JoinChannel("#a", null), run("/join #a"))
        assertEquals(CommandResult.JoinChannel("#a", "secreto"), run("/join #a secreto"))
    }

    @Test
    fun part_distingueCanalMensaje() {
        assertEquals(CommandResult.PartChannel(null, null), run("/part"))
        assertEquals(CommandResult.PartChannel("#a", null), run("/part #a"))
        assertEquals(CommandResult.PartChannel("#a", "hasta luego"), run("/part #a hasta luego"))
        assertEquals(CommandResult.PartChannel(null, "solo mensaje"), run("/part solo mensaje"))
    }

    @Test
    fun away_activaYDesactiva() {
        run("/away comiendo")
        run("/away")
        assertEquals(listOf("AWAY :comiendo", "AWAY"), sender.raw)
    }

    // --- /msg, /query
    @Test
    fun msg_validaArgumentosYDestinos() {
        assertEquals(CommandResult.InvalidArguments("CMD_MSG_USAGE"), run("/msg ana"))
        assertEquals(CommandResult.ShowSystemMessage("#chat", "CMD_MSG_SELF", true), run("/msg yo hola"))
        assertEquals(
            CommandResult.ShowSystemMessage("#chat", "CMD_MSG_SERVER_TAB|Servidor", true),
            run("/msg servidor hola")
        )
    }

    @Test
    fun msg_aUnUsuario_enviaYAnadeMensajeLocalMasAviso() {
        assertEquals(CommandResult.Handled, run("/msg Ana hola que tal"))
        assertEquals("Ana" to "hola que tal", sender.messages.single())
        val msgs = state.allMessages.value.getValue("Ana")
        assertEquals(UiMessageType.PRIVATE_MSG_SENT, msgs.first().type)
        assertEquals("<Yo> hola que tal", msgs.first().fullText)
        assertEquals("CMD_MSG_SENT|Ana", msgs.last().fullText)
    }

    @Test
    fun query_validaDestinos() {
        assertEquals(CommandResult.InvalidArguments("CMD_QUERY_USAGE"), run("/query"))
        assertEquals(CommandResult.ShowSystemMessage("#chat", "CMD_QUERY_SELF", true), run("/query yo"))
        assertEquals(CommandResult.ShowSystemMessage("#chat", "CMD_QUERY_CHANNEL", true), run("/query #otro"))
        assertEquals(CommandResult.OpenQuery("Ana", "hola"), run("/query Ana hola"))
        assertEquals(CommandResult.OpenQuery("Ana", null), run("/query Ana"))
    }

    // --- /topic, /clear, /help
    @Test
    fun topic_enCanalSinArgumentos_pideElTema() {
        assertEquals(CommandResult.Handled, run("/topic"))
        assertEquals(listOf("TOPIC #chat"), sender.raw)
    }

    @Test
    fun topic_conCanalExplicitoYTema_loCambia() {
        assertEquals(CommandResult.Handled, run("/topic #otro nuevo tema", active = "Ana"))
        assertEquals(listOf("TOPIC #otro :nuevo tema"), sender.raw)
    }

    @Test
    fun topic_fueraDeUnCanalSinCanalExplicito_muestraUso() {
        assertEquals(CommandResult.InvalidArguments("CMD_TOPIC_USAGE"), run("/topic algo", active = "Ana"))
    }

    @Test
    fun clear_conYSinVentanaActiva() {
        assertEquals(CommandResult.ClearWindow("#chat"), run("/clear"))
        assertEquals(
            CommandResult.ShowSystemMessage(ChatStateManager.SERVER_TARGET_ID, "CMD_CLEAR_NO_WINDOW", true),
            run("/clear", active = null)
        )
    }

    @Test
    fun help_devuelveLaAyuda() {
        assertEquals(CommandResult.ShowSystemMessage("#chat", "CMD_HELP"), run("/help"))
    }
}
