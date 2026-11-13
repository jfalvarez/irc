package com.jfaf.irc.data.model

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.Test

class IrcLineParserTest {

    private fun parse(line: String): ParsedIrcMessage {
        val msg = IrcLineParser.parse(line)
        assertNotNull(msg, "Se esperaba un mensaje para: $line")
        return msg
    }

    @Test
    fun privmsg_conPrefijoYTrailing() {
        val msg = parse(":nick!user@host PRIVMSG #chan :hola mundo")
        assertEquals("nick!user@host", msg.prefix)
        assertEquals("PRIVMSG", msg.command)
        assertEquals(listOf("#chan"), msg.params)
        assertEquals("hola mundo", msg.trailing)
        assertEquals("nick", msg.senderNickname)
    }

    @Test
    fun ping_sinPrefijo() {
        val msg = parse("PING :irc.server")
        assertNull(msg.prefix)
        assertEquals("PING", msg.command)
        assertEquals(emptyList<String>(), msg.params)
        assertEquals("irc.server", msg.trailing)
    }

    @Test
    fun numerico_001() {
        val msg = parse(":server 001 me :Welcome to the network")
        assertEquals("001", msg.command)
        assertEquals(listOf("me"), msg.params)
        assertEquals("Welcome to the network", msg.trailing)
    }

    @Test
    fun join_sinTrailing() {
        val msg = parse(":n!u@h JOIN #chan")
        assertEquals("JOIN", msg.command)
        assertEquals(listOf("#chan"), msg.params)
        assertNull(msg.trailing)
    }

    @Test
    fun trailing_conDosPuntosYEspacios() {
        val msg = parse(":n!u@h PRIVMSG #c :hola :) y más")
        assertEquals("hola :) y más", msg.trailing)
    }

    @Test
    fun mode_conVariosParametros() {
        val msg = parse(":n!u@h MODE #c +o otro")
        assertEquals(listOf("#c", "+o", "otro"), msg.params)
        assertNull(msg.trailing)
    }

    @Test
    fun names_353() {
        val msg = parse(":s 353 me = #c :a b c")
        assertEquals(listOf("me", "=", "#c"), msg.params)
        assertEquals("a b c", msg.trailing)
    }

    @Test
    fun espaciosDobles_seToleran() {
        val msg = parse(":s  MODE   #c   +o")
        assertEquals("s", msg.prefix)
        assertEquals("MODE", msg.command)
        assertEquals(listOf("#c", "+o"), msg.params)
    }

    @Test
    fun prefijoSinEspacio_devuelveNull() {
        assertNull(IrcLineParser.parse(":soloprefijo"))
    }

    @Test
    fun lineaVaciaOEnBlanco_devuelveNull() {
        assertNull(IrcLineParser.parse(""))
        assertNull(IrcLineParser.parse("   "))
    }

    @Test
    fun tagsIrcV3_seIgnoranYRawLineSeConserva() {
        val line = "@time=2024-01-01T00:00:00Z;msgid=1 :n!u@h PRIVMSG #c :hi"
        val msg = parse(line)
        assertEquals("n!u@h", msg.prefix)
        assertEquals("PRIVMSG", msg.command)
        assertEquals(listOf("#c"), msg.params)
        assertEquals("hi", msg.trailing)
        assertEquals(line, msg.rawLine)
    }
}
