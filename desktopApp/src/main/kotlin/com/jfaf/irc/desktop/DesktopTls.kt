package com.jfaf.irc.desktop

import com.jfaf.irc.log.Log
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Confianza TLS de escritorio. El JDK sólo confía en su propio `cacerts`, así que no reconoce las raíces
 * que el usuario o su red han instalado en el sistema (antivirus que revisa el TLS, proxy de empresa…).
 * En Windows se acepta además lo que confíe el almacén "Windows-ROOT"; en el resto de sistemas, el del JDK.
 */
object DesktopTls {
    private const val TAG = "DesktopTls"

    /** Registra el `SSLContext` por defecto; hay que llamarlo antes de abrir ninguna conexión TLS. */
    fun install() {
        val managers = buildList {
            add(trustManager(null))
            if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
                runCatching {
                    val store = KeyStore.getInstance("Windows-ROOT").apply { load(null, null) }
                    add(trustManager(store))
                }.onFailure { Log.w(TAG, "No se pudo leer el almacén Windows-ROOT", it) }
            }
        }
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(CompositeTrustManager(managers)), null)
        SSLContext.setDefault(context)
    }

    private fun trustManager(store: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(store)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}

/** Acepta una cadena si la acepta alguno de los [managers]; si ninguno, lanza el primer error. */
class CompositeTrustManager(private val managers: List<X509TrustManager>) : X509TrustManager {

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
        firstThatAccepts { it.checkServerTrusted(chain, authType) }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        firstThatAccepts { it.checkClientTrusted(chain, authType) }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        managers.flatMap { it.acceptedIssuers.asList() }.toTypedArray()

    private fun firstThatAccepts(check: (X509TrustManager) -> Unit) {
        var firstError: CertificateException? = null
        for (manager in managers) {
            try {
                check(manager)
                return
            } catch (e: CertificateException) {
                if (firstError == null) firstError = e
            }
        }
        throw firstError ?: CertificateException("Sin gestores de confianza")
    }
}
