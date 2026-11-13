package com.jfaf.irc.data.prefs

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class ServerConfigTest {

    @Test
    fun valoresPorDefecto() {
        val c = ServerConfig()
        assertEquals("irc.irc-hispano.org", c.host)
        assertEquals(6667, c.portFor(ssl = false))
        assertEquals(6697, c.portFor(ssl = true))
    }

    @Test
    fun parse_aceptaDatosValidosYRecortaEspacios() {
        assertEquals(ServerConfig("irc.libera.chat", 6667, 6697), ServerConfig.parse("  irc.libera.chat ", " 6667", "6697 "))
        assertEquals(ServerConfig("localhost", 1, 65535), ServerConfig.parse("localhost", "1", "65535"))
    }

    @Test
    fun parse_rechazaHostInvalido() {
        assertNull(ServerConfig.parse("", "6667", "6697"))
        assertNull(ServerConfig.parse("irc libera", "6667", "6697"))
        assertNull(ServerConfig.parse("irc.libera.chat/x", "6667", "6697"))
        assertNull(ServerConfig.parse(".irc.com", "6667", "6697"))
    }

    @Test
    fun parse_rechazaPuertosInvalidos() {
        assertNull(ServerConfig.parse("irc.x.org", "0", "6697"))
        assertNull(ServerConfig.parse("irc.x.org", "6667", "65536"))
        assertNull(ServerConfig.parse("irc.x.org", "abc", "6697"))
        assertNull(ServerConfig.parse("irc.x.org", "6667", ""))
    }
}
