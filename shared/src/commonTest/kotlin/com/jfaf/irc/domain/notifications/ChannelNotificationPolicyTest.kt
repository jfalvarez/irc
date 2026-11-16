package com.jfaf.irc.domain.notifications

import com.jfaf.irc.data.model.IrcLineParser
import com.jfaf.irc.domain.notifications.ChannelNotificationLevel.ALL
import com.jfaf.irc.domain.notifications.ChannelNotificationLevel.MENTIONS
import com.jfaf.irc.domain.notifications.ChannelNotificationLevel.MUTED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChannelNotificationPolicyTest {

    private fun msg(line: String) = IrcLineParser.parse(line)!!
    private fun decide(line: String, level: ChannelNotificationLevel, own: String? = "ElTester", ignored: Set<String> = emptySet()) =
        ChannelNotificationPolicy.decide(msg(line), level, own, ignored)

    private val plain = ":Ana!u@h PRIVMSG #chat :hola a todos"
    private val mention = ":Ana!u@h PRIVMSG #chat :eh ElTester, mira esto"

    @Test
    fun reconoceUnMensajeDeCanal() {
        assertTrue(ChannelNotificationPolicy.isChannelMessage(msg(plain)))
        assertFalse(ChannelNotificationPolicy.isChannelMessage(msg(":Ana!u@h PRIVMSG ElTester :hola")))
        assertFalse(ChannelNotificationPolicy.isChannelMessage(msg(":Ana!u@h NOTICE #chat :hola")))
        assertFalse(ChannelNotificationPolicy.isChannelMessage(msg(":Ana!u@h JOIN #chat")))
    }

    @Test
    fun soloMenciones_avisaSiMeNombran() {
        assertEquals(ChannelAlert("#chat", "Ana", "eh ElTester, mira esto", isMention = true), decide(mention, MENTIONS))
        assertNull(decide(plain, MENTIONS))
    }

    @Test
    fun todo_avisaDeCadaMensaje_yMarcaLasMenciones() {
        assertEquals(ChannelAlert("#chat", "Ana", "hola a todos", isMention = false), decide(plain, ALL))
        assertTrue(decide(mention, ALL)!!.isMention)
    }

    @Test
    fun silenciado_noAvisaDeNada_niDeMenciones() {
        assertNull(decide(plain, MUTED))
        assertNull(decide(mention, MUTED))
    }

    @Test
    fun laMencionNoDistingueMayusculasNiMeteNicksParecidos() {
        assertTrue(decide(":Ana!u@h PRIVMSG #chat :hola @eltester", MENTIONS) != null)
        assertNull(decide(":Ana!u@h PRIVMSG #chat :hola ElTester2", MENTIONS))
        assertNull(decide(":Ana!u@h PRIVMSG #chat :hola Tester", MENTIONS))
    }

    @Test
    fun sinSaberMiNick_soloAvisaConTodo() {
        assertNull(decide(mention, MENTIONS, own = null))
        assertNull(decide(mention, MENTIONS, own = ""))
        assertTrue(decide(plain, ALL, own = null) != null)
    }

    @Test
    fun nuncaAvisaDeLosIgnorados_niDeUnoMismo() {
        assertNull(decide(mention, ALL, ignored = setOf("ANA")))
        assertNull(decide(":eltester!u@h PRIVMSG #chat :yo mismo", ALL))
    }

    @Test
    fun sinRemitente_noAvisa() {
        assertNull(decide("PRIVMSG #chat :sin prefijo", ALL))
    }

    @Test
    fun unaAccionSeMuestraComoTal_yCuentaParaLasMenciones() {
        val action = decide(":Ana!u@h PRIVMSG #chat :\u0001ACTION saluda a ElTester\u0001", MENTIONS)!!
        assertEquals("* saluda a ElTester", action.text)
        assertTrue(action.isMention)
    }

    @Test
    fun noEsDeCanal_noHayAviso() {
        assertNull(decide(":Ana!u@h PRIVMSG ElTester :hola", ALL))
    }
}
