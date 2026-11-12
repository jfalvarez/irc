package com.jfaf.irc.data.prefs

/** Servidor IRC al que se conecta la app. */
data class ServerConfig(
    val host: String = DEFAULT_HOST,
    val plainPort: Int = DEFAULT_PLAIN_PORT,
    val sslPort: Int = DEFAULT_SSL_PORT
) {
    fun portFor(ssl: Boolean): Int = if (ssl) sslPort else plainPort

    companion object {
        const val DEFAULT_HOST = "irc.irc-hispano.org"
        const val DEFAULT_PLAIN_PORT = 6667
        const val DEFAULT_SSL_PORT = 6697

        private val HOST_REGEX = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$")

        /** Valida lo escrito por el usuario; null si el host o algún puerto no son válidos. */
        fun parse(host: String, plainPort: String, sslPort: String): ServerConfig? {
            val cleanHost = host.trim()
            if (!HOST_REGEX.matches(cleanHost)) return null
            val plain = plainPort.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val ssl = sslPort.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            return ServerConfig(cleanHost, plain, ssl)
        }
    }
}
