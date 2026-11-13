package com.jfaf.irc.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkFinderTest {

    private fun single(text: String): FoundLink = LinkFinder.find(text).single()

    @Test
    fun conEsquema_quitaPuntuacionFinal() {
        val l = single("mira https://example.com/a?b=1.")
        assertEquals("https://example.com/a?b=1", l.text)
        assertEquals("https://example.com/a?b=1", l.target)
        assertEquals(5, l.start)
        assertEquals(5 + l.text.length, l.end)
    }

    @Test
    fun dominioSinEsquema_anadeHttps() {
        val l = single("visita example.com ya")
        assertEquals("example.com", l.text)
        assertEquals("https://example.com", l.target)
    }

    @Test
    fun www_entreParentesis_quitaParentesis() {
        val l = single("(www.foo.es/x)")
        assertEquals("www.foo.es/x", l.text)
        assertEquals(1, l.start)
    }

    @Test
    fun puertoYRuta() = assertEquals("host.com:8080/path", single("host.com:8080/path").text)

    @Test
    fun ipv4ConPuerto() {
        val l = single("192.168.1.10:8080")
        assertEquals("192.168.1.10:8080", l.text)
        assertEquals("https://192.168.1.10:8080", l.target)
    }

    @Test
    fun ftp_mantieneEsquema() = assertEquals("ftp://host/file", single("ftp://host/file").target)

    @Test
    fun dosEnlaces_conSusPosiciones() {
        val links = LinkFinder.find("a.com y b.org/x")
        assertEquals(listOf("a.com", "b.org/x"), links.map { it.text })
        assertEquals(listOf(0, 8), links.map { it.start })
    }

    @Test
    fun mayusculas_seMantienen() =
        assertEquals("HTTPS://Example.COM", single("HTTPS://Example.COM").target)

    @Test
    fun dominioInternacionalizado() = assertEquals("españa.es", single("españa.es").text)

    @Test
    fun sinEnlaces() {
        assertTrue(LinkFinder.find("versión 1.2.3").isEmpty())
        assertTrue(LinkFinder.find("sin enlaces, fin.").isEmpty())
    }
}
