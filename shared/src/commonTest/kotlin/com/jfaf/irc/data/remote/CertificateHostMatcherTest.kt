package com.jfaf.irc.data.remote

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CertificateHostMatcherTest {

    @Test
    fun coincidenciaExactaSinDistinguirMayusculas() {
        assertTrue(CertificateHostMatcher.matches("Irc.Example.org", listOf("irc.example.org")))
        assertTrue(CertificateHostMatcher.matches("irc.example.org.", listOf("IRC.EXAMPLE.ORG")))
    }

    @Test
    fun valeCualquieraDeLosNombresDelCertificado() {
        assertTrue(CertificateHostMatcher.matches("chatmovil.chathispano.com", listOf("otro.org", "chatmovil.chathispano.com")))
    }

    @Test
    fun nombreDistintoNoCoincide() {
        assertFalse(CertificateHostMatcher.matches("irc.example.org", listOf("chatmovil.chathispano.com")))
        assertFalse(CertificateHostMatcher.matches("irc.example.org", emptyList()))
        assertFalse(CertificateHostMatcher.matches("", listOf("")))
    }

    @Test
    fun comodinCubreUnaSolaEtiqueta() {
        assertTrue(CertificateHostMatcher.matches("irc.example.org", listOf("*.example.org")))
        assertFalse(CertificateHostMatcher.matches("a.irc.example.org", listOf("*.example.org")))
        assertFalse(CertificateHostMatcher.matches("example.org", listOf("*.example.org")))
    }

    @Test
    fun comodinDeUnDominioDeNivelSuperiorNoVale() {
        assertFalse(CertificateHostMatcher.matches("example.com", listOf("*.com")))
    }

    @Test
    fun comodinNoCoincideConUnaEtiquetaVacia() {
        assertFalse(CertificateHostMatcher.matches(".example.org", listOf("*.example.org")))
    }
}
