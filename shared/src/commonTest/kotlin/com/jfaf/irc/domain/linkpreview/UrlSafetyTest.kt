package com.jfaf.irc.domain.linkpreview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlSafetyTest {

    @Test
    fun laWebsPublicasSePueden() {
        listOf(
            "https://example.com", "http://example.com/a/b?x=1#f", "https://www.elpais.com/noticia", "https://sub.dominio.es:8443/x",
            "https://8.8.8.8/", "https://[2001:db8::1]/x", "https://bad.cafe/"
        ).forEach { assertTrue(UrlSafety.isPublicHttpUrl(it), "Debería poder: $it") }
    }

    @Test
    fun otrosEsquemasNo() {
        listOf("ftp://example.com/x", "file:///etc/passwd", "javascript:alert(1)", "example.com", "data:text/html,hola", "//example.com")
            .forEach { assertFalse(UrlSafety.isPublicHttpUrl(it), "No debería poder: $it") }
    }

    @Test
    fun localesYPrivadosNo() {
        listOf(
            "http://localhost/", "http://LOCALHOST:8080/", "http://foo.localhost/", "http://router/", "http://intranet/x",
            "http://mi-nas.local/", "http://servidor.lan/", "http://app.internal/", "http://127.0.0.1/", "http://127.1.2.3/",
            "http://10.0.0.5/", "http://172.16.0.1/", "http://172.31.255.255/", "http://192.168.1.1/", "http://169.254.169.254/latest/meta-data",
            "http://100.64.0.1/", "http://0.0.0.0/", "http://224.0.0.1/", "http://255.255.255.255/", "http://[::1]/", "http://[fe80::1]/",
            "http://[fc00::1]/", "http://[fd12:3456::1]/", "http://[::ffff:10.0.0.1]/", "http://[::ffff:a00:1]/", "http://[::]/"
        ).forEach { assertFalse(UrlSafety.isPublicHttpUrl(it), "No debería poder: $it") }
    }

    @Test
    fun laLimitesDeLosRangosPrivados() {
        assertTrue(UrlSafety.isPublicHttpUrl("http://172.15.0.1/"))
        assertTrue(UrlSafety.isPublicHttpUrl("http://172.32.0.1/"))
        assertTrue(UrlSafety.isPublicHttpUrl("http://100.63.0.1/"))
        assertTrue(UrlSafety.isPublicHttpUrl("http://100.128.0.1/"))
        assertTrue(UrlSafety.isPublicHttpUrl("http://192.169.0.1/"))
    }

    @Test
    fun laIpEscritaDeFormaRaraSeRechaza() {
        // 2130706433 = 127.0.0.1; 0x7f.1 y 0177.0.0.1 también; son la forma típica de colar loopback
        listOf("http://2130706433/", "http://0x7f.1/", "http://0x7f000001/", "http://0177.0.0.1/", "http://127.1/", "http://1.2.3/")
            .forEach { assertFalse(UrlSafety.isPublicHttpUrl(it), "No debería poder: $it") }
    }

    @Test
    fun usuarioYContrasenaEnLaUrlSeRechaza() {
        assertFalse(UrlSafety.isPublicHttpUrl("https://banco.com@evil.example/"))
        assertFalse(UrlSafety.isPublicHttpUrl("https://user:pw@example.com/"))
    }

    @Test
    fun hostOfQuitaPuertoCorchetesYMayusculas() {
        assertEquals("example.com", UrlSafety.hostOf("https://Example.COM:8443/x?y#z"))
        assertEquals("2001:db8::1", UrlSafety.hostOf("http://[2001:db8::1]:80/"))
        assertEquals("example.com", UrlSafety.hostOf("https://example.com./"))
    }
}
