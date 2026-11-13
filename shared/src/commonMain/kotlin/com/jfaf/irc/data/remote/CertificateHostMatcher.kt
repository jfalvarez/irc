package com.jfaf.irc.data.remote

/**
 * Comprobación del nombre de un certificado TLS (reglas de RFC 6125): coincidencia exacta sin distinguir
 * mayúsculas, o un comodín `*.dominio` en la primera etiqueta (que cubre exactamente una etiqueta).
 *
 * Es lógica propia porque el `HostnameVerifier` por defecto del JDK de escritorio rechaza todo
 * (el de Android sí comprueba), y así Android y escritorio verifican igual.
 */
object CertificateHostMatcher {

    /** `true` si [expectedHost] coincide con alguno de los [certNames] del certificado. */
    fun matches(expectedHost: String, certNames: List<String>): Boolean {
        val host = normalize(expectedHost)
        if (host.isEmpty()) return false
        return certNames.any { matchesOne(host, normalize(it)) }
    }

    private fun matchesOne(host: String, pattern: String): Boolean {
        if (pattern.isEmpty()) return false
        if (!pattern.startsWith("*.")) return host == pattern
        val suffix = pattern.substring(2)
        if ('.' !in suffix) return false // `*.com` no vale
        val firstDot = host.indexOf('.')
        return firstDot > 0 && host.substring(firstDot + 1) == suffix
    }

    private fun normalize(name: String) = name.trim().trimEnd('.').lowercase()
}
