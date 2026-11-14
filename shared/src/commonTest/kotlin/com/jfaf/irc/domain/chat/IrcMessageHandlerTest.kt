package com.jfaf.irc.domain.chat

import com.jfaf.irc.data.model.IrcLineParser
import com.jfaf.irc.domain.config.AppConfig
import com.jfaf.irc.domain.config.parseKeywordList
import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class IrcMessageHandlerTest {

    /** Texto estable y legible en los tests: "CLAVE|arg1|arg2". */
    private val fakeStrings = object : StringProvider {
        override fun get(msg: Msg, vararg args: Any) = (listOf(msg.name) + args.map { it.toString() }).joinToString("|")
    }
    private fun config(vararg keywords: String) = object : AppConfig {
        override val imageFilterKeywords = keywords.toSet()
        override val googleSignInClientId = ""
    }
    private val handler = IrcMessageHandler(fakeStrings, config())

    private fun snapshot(
        nick: String = "Yo",
        active: String? = "Servidor",
        targets: List<String> = listOf("Servidor"),
        messages: Map<String, List<UiChatMessage>> = emptyMap(),
        unread: Set<String> = emptySet(),
        users: Map<String, List<String>> = emptyMap()
    ) = ChatUiSnapshot(nick, active, messages, targets, unread, users)

    private fun process(line: String, s: ChatUiSnapshot = snapshot()) =
        handler.processMessage(s, IrcLineParser.parse(line)!!)

    @Test
    fun mensajeDeCanal_seAnadeAlCanalYMarcaNoLeido() {
        val r = process(":Ana!u@h PRIVMSG #chat :hola a todos", snapshot(active = "Servidor", targets = listOf("Servidor", "#chat")))
        val msg = r.newAllMessages.getValue("#chat").single()
        assertEquals(UiMessageType.CHANNEL_MSG_RECEIVED, msg.type)
        assertEquals("<Ana> hola a todos", msg.fullText)
        assertEquals("Ana", msg.sender)
        assertTrue("#chat" in r.newUnreadTargets)
        assertNull(r.privateMessageEventNick)
    }

    @Test
    fun mensajeEnElCanalActivo_noQuedaSinLeer() {
        val r = process(":Ana!u@h PRIVMSG #chat :hola", snapshot(active = "#chat", targets = listOf("Servidor", "#chat")))
        assertFalse("#chat" in r.newUnreadTargets)
    }

    @Test
    fun mensajePrivado_creaPestanaYAvisa() {
        val r = process(":Ana!u@h PRIVMSG Yo :psst")
        assertTrue("Ana" in r.newChatTargets)
        assertEquals("Ana", r.privateMessageEventNick)
        assertEquals(UiMessageType.PRIVATE_MSG_RECEIVED, r.newAllMessages.getValue("Ana").single().type)
        assertTrue("Ana" in r.newUnreadTargets)
    }

    @Test
    fun mensajePropioAUnPrivado_vaAlTargetDestinoYNoAvisa() {
        val r = process(":Yo!u@h PRIVMSG Ana :hola", snapshot(targets = listOf("Servidor", "Ana")))
        val msg = r.newAllMessages.getValue("Ana").single()
        assertEquals(UiMessageType.PRIVATE_MSG_SENT, msg.type)
        assertTrue(msg.isOwnMessage)
        assertNull(r.privateMessageEventNick)
    }

    @Test
    fun enlaceDeImagen_seDetectaComoMedia() {
        val r = process(":Ana!u@h PRIVMSG #chat :mira https://example.com/foto.png", snapshot(targets = listOf("Servidor", "#chat")))
        val msg = r.newAllMessages.getValue("#chat").single()
        assertEquals("https://example.com/foto.png", msg.mediaUrl)
    }

    private fun mediaOf(url: String): UiChatMessage =
        process(":Ana!u@h PRIVMSG #chat :mira $url", snapshot(targets = listOf("Servidor", "#chat")))
            .newAllMessages.getValue("#chat").single()

    @Test
    fun enlaceDeVideoMp4_seDetectaComoVideo() {
        val msg = mediaOf("https://example.com/clip.mp4")
        assertEquals("https://example.com/clip.mp4", msg.mediaUrl)
        assertEquals(com.jfaf.irc.domain.chat.model.MediaTypeEnum.VIDEO, msg.mediaType)
    }

    @Test
    fun enlaceDeVideoMp4_conConsultaYMayusculas_seDetectaComoVideo() {
        assertEquals(com.jfaf.irc.domain.chat.model.MediaTypeEnum.VIDEO, mediaOf("https://example.com/CLIP.MP4?t=10").mediaType)
    }

    @Test
    fun enlaceDeVideoWebm_sigueDetectandoseComoVideo() {
        assertEquals(com.jfaf.irc.domain.chat.model.MediaTypeEnum.VIDEO, mediaOf("https://example.com/clip.webm").mediaType)
    }

    @Test
    fun noConfundeConOtrasExtensionesParecidas() {
        assertEquals(com.jfaf.irc.domain.chat.model.MediaTypeEnum.NONE, mediaOf("https://example.com/clip.mp4.txt").mediaType)
        assertEquals(com.jfaf.irc.domain.chat.model.MediaTypeEnum.NONE, mediaOf("https://example.com/clip.mp3").mediaType)
    }

    @Test
    fun imagenConPalabraFiltrada_noSePrevisualiza() {
        val filtering = IrcMessageHandler(fakeStrings, config("nsfw"))
        val r = filtering.processMessage(
            snapshot(targets = listOf("Servidor", "#chat")),
            IrcLineParser.parse(":Ana!u@h PRIVMSG #chat :mira https://example.com/NSFW/foto.png")!!
        )
        assertNull(r.newAllMessages.getValue("#chat").single().mediaUrl)
    }

    @Test
    fun parseKeywordList_limpiaYNormaliza() {
        assertEquals(setOf("a", "b", "c"), parseKeywordList(" a, B ,,c "))
        assertTrue(parseKeywordList("").isEmpty())
    }

    @Test
    fun propioJoin_abrePestanaYLaActiva() {
        val r = process(":Yo!u@h JOIN :#nuevo")
        assertTrue("#nuevo" in r.newChatTargets)
        assertEquals("#nuevo", r.newActiveTarget)
        assertEquals(listOf("Yo"), r.newUsersInChannel["#nuevo"])
    }

    @Test
    fun joinAjeno_anadeUsuarioYMensajeSinCambiarDePestana() {
        val s = snapshot(active = "#chat", targets = listOf("Servidor", "#chat"), users = mapOf("#chat" to listOf("Yo")))
        val r = process(":Ana!u@h JOIN :#chat", s)
        assertEquals("#chat", r.newActiveTarget)
        assertTrue(r.newUsersInChannel.getValue("#chat").any { it == "Ana" })
        assertEquals("EVT_JOIN|Ana|#chat", r.newAllMessages.getValue("#chat").single().fullText)
    }

    @Test
    fun partAjeno_quitaAlUsuarioDeLaLista() {
        val s = snapshot(targets = listOf("Servidor", "#chat"), users = mapOf("#chat" to listOf("Yo", "Ana")))
        val r = process(":Ana!u@h PART #chat :adios", s)
        assertFalse(r.newUsersInChannel.getValue("#chat").any { it == "Ana" })
    }

    @Test
    fun namesReply_rellenaLaListaDeUsuarios() {
        val r = process(":srv 353 Yo = #chat :@op +voz normal")
        val users = r.newUsersInChannel.getValue("#chat")
        assertTrue(users.containsAll(listOf("@op", "+voz", "normal")))
    }

    @Test
    fun cambioDeNickPropio_actualizaElNick() {
        val s = snapshot(targets = listOf("Servidor", "#chat"), users = mapOf("#chat" to listOf("Yo")))
        val r = process(":Yo!u@h NICK :Nuevo", s)
        assertEquals("Nuevo", r.newCurrentNickname)
        assertEquals("Nuevo", r.ownNickChangedTo)
    }

    @Test
    fun cambioDeNickAjeno_noCambiaElPropio() {
        val r = process(":Ana!u@h NICK :Ana2")
        assertEquals("Yo", r.newCurrentNickname)
        assertNull(r.ownNickChangedTo)
    }

    @Test
    fun bienvenida001_confirmaElNickDelServidor() {
        val r = process(":srv 001 YoReal :Bienvenido a la red")
        assertEquals("YoReal", r.newCurrentNickname)
    }

    @Test
    fun errorNickNoExistente401_vaALaPestanaServidor() {
        val r = process(":srv 401 Yo Fantasma :No such nick")
        val msg = r.newAllMessages.getValue("Servidor").single()
        assertTrue(msg.fullText.contains("Fantasma"))
    }

    @Test
    fun kickAlUsuarioPropio_generaMensajeDeExpulsion() {
        val s = snapshot(active = "#chat", targets = listOf("Servidor", "#chat"), users = mapOf("#chat" to listOf("Yo", "Op")))
        val r = process(":Op!u@h KICK #chat Yo :fuera", s)
        assertTrue(r.newAllMessages.values.flatten().any { it.fullText.startsWith("EVT_KICK|Yo|#chat|Op") })
    }
}
