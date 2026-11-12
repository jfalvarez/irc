package com.jfaf.irc.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class TlsHostPolicyTest {

    @Test
    fun hostSinAlias_soloSeVerificaContraSiMismo() {
        assertEquals(listOf("irc.libera.chat"), TlsHostPolicy.acceptedCertificateHosts("irc.libera.chat"))
    }

    @Test
    fun ircHispano_aceptaElNombreRealDelCertificado() {
        assertEquals(
            listOf("irc.irc-hispano.org", "chatmovil.chathispano.com"),
            TlsHostPolicy.acceptedCertificateHosts("irc.irc-hispano.org")
        )
        assertEquals(2, TlsHostPolicy.acceptedCertificateHosts("IRC.IRC-HISPANO.ORG").size)
    }
}
