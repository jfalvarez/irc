package com.jfaf.irc.data.remote

/**
 * Nombres que se aceptan en el certificado TLS de un servidor, además del propio host.
 *
 * Algunos servidores se alcanzan por un alias DNS y su certificado está emitido para otro nombre:
 * `irc.irc-hispano.org` presenta un certificado para `chatmovil.chathispano.com`.
 */
object TlsHostPolicy {

    private val CERTIFICATE_ALIASES = mapOf(
        "irc.irc-hispano.org" to listOf("chatmovil.chathispano.com")
    )

    /** Nombres contra los que se verifica el certificado: primero el host, luego sus alias conocidos. */
    fun acceptedCertificateHosts(host: String): List<String> =
        listOf(host) + CERTIFICATE_ALIASES[host.lowercase()].orEmpty()
}
