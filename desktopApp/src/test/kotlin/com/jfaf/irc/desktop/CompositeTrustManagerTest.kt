package com.jfaf.irc.desktop

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CompositeTrustManagerTest {

    private class FakeManager(private val accepts: Boolean, private val message: String = "no") : X509TrustManager {
        var calls = 0
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            calls++
            if (!accepts) throw CertificateException(message)
        }
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = checkServerTrusted(chain, authType)
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    @Test
    fun aceptaSiAlgunoAcepta() {
        val first = FakeManager(accepts = false)
        val second = FakeManager(accepts = true)
        CompositeTrustManager(listOf(first, second)).checkServerTrusted(emptyArray(), "RSA")
        assertEquals(1, first.calls)
        assertEquals(1, second.calls)
    }

    @Test
    fun siAceptaElPrimeroNoPreguntaAlResto() {
        val first = FakeManager(accepts = true)
        val second = FakeManager(accepts = true)
        CompositeTrustManager(listOf(first, second)).checkServerTrusted(emptyArray(), "RSA")
        assertEquals(0, second.calls)
    }

    @Test
    fun siNingunoAceptaLanzaElPrimerError() {
        val composite = CompositeTrustManager(listOf(FakeManager(false, "primero"), FakeManager(false, "segundo")))
        val error = assertFailsWith<CertificateException> { composite.checkServerTrusted(emptyArray(), "RSA") }
        assertEquals("primero", error.message)
    }
}
