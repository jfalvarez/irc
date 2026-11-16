package com.jfaf.irc.domain.linkpreview

/**
 * Qué direcciones se pueden pedir para una vista previa (UI-31). El chat trae enlaces de desconocidos: pedir uno que
 * apunte a `localhost`, al router o a otro equipo de la red local sería hacer que la app consulte la red del usuario
 * por cuenta del remitente. Sólo se piden webs públicas por http(s). Lógica pura y con tests; la comprobación de lo
 * que resuelve el DNS la repite la implementación de cada plataforma.
 */
object UrlSafety {

    /** http(s), sin usuario ni contraseña en la URL, y con un host que no sea local ni privado. */
    fun isPublicHttpUrl(url: String): Boolean {
        val scheme = url.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return false
        val authority = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty() || '@' in authority) return false
        val host = hostOf(url) ?: return false
        return !isPrivateHost(host)
    }

    /** El host de la URL en minúsculas y sin puerto ni corchetes de IPv6; null si no se puede leer. */
    fun hostOf(url: String): String? {
        val authority = url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty()) return null
        val host = if (authority.startsWith("[")) {
            authority.substringAfter('[').substringBefore(']')
        } else {
            authority.substringBefore(':')
        }
        return host.lowercase().trimEnd('.').takeIf { it.isNotEmpty() }
    }

    /** Si el host (nombre o IP escrita) es local, privado, reservado o una IP escrita de forma rara. */
    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h.isEmpty()) return true
        if (':' in h) return isPrivateIpv6(h)
        // Un nombre sin punto (`router`, `intranet`) sólo existe en la red local
        if ('.' !in h && h.any { !it.isDigit() && it !in 'a'..'f' && it != 'x' }) return true
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") ||
            h.endsWith(".lan") || h.endsWith(".home") || h.endsWith(".intranet") || h.endsWith(".corp") || h.endsWith(".private")
        ) return true
        // Parece una IP (sólo cifras, puntos y hexadecimal): se exige la forma normal a.b.c.d; las raras (decimal
        // entero, hexadecimal, octal) son la forma habitual de colar 127.0.0.1
        if (h.all { it.isDigit() || it == '.' || it in 'a'..'f' || it == 'x' } && h.any { it.isDigit() }) {
            val octets = ipv4Octets(h) ?: return true
            return isPrivateIpv4(octets)
        }
        return false
    }

    private fun ipv4Octets(text: String): List<Int>? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        return parts.map { part ->
            if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
            if (part.length > 1 && part.startsWith("0")) return null // «010» se lee como octal en algunas pilas
            part.toInt().takeIf { it in 0..255 } ?: return null
        }
    }

    /** Reservadas: este equipo, privadas, CGNAT, loopback, enlace local, pruebas, multidifusión y futuras. */
    fun isPrivateIpv4(o: List<Int>): Boolean {
        val (a, b) = o[0] to o[1]
        return a == 0 || a == 10 || a == 127 || a >= 224 ||
            (a == 100 && b in 64..127) ||
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 192 && b == 0 && o[2] == 0) ||
            (a == 198 && b in 18..19)
    }

    private fun isPrivateIpv6(h: String): Boolean {
        if (h == "::" || h == "::1") return true
        // IPv4 dentro de IPv6 (::ffff:10.0.0.1): se juzga la IPv4
        val tail = h.substringAfterLast(':')
        if ('.' in tail) return ipv4Octets(tail)?.let { isPrivateIpv4(it) } ?: true
        val first = h.substringBefore(':').ifEmpty { "0" }.toIntOrNull(16) ?: return true
        // Empieza por «::»: sin especificar, loopback, compatible o mapeada con IPv4 (::ffff:a00:1 es 10.0.0.1)
        if (first == 0) return true
        // fc00::/7 (privadas), fe80::/10 (enlace local), ff00::/8 (multidifusión)
        return (first and 0xFE00) == 0xFC00 || (first and 0xFFC0) == 0xFE80 || (first and 0xFF00) == 0xFF00
    }
}
