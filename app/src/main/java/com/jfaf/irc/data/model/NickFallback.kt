package com.jfaf.irc.data.model

/**
 * Nicks alternativos para registrarse cuando el servidor responde 433 (nick en uso).
 * Típico al reconectar: el servidor aún conserva la sesión "fantasma" anterior durante ~1-2 min.
 */
object NickFallback {

    const val MAX_ATTEMPTS = 4
    private const val MAX_NICK_LENGTH = 30

    /** Alternativa número [attempt] (empezando en 1): `nick_`, `nick__`, `nick_1`, `nick_2`... o null si se agotaron. */
    fun alternative(base: String, attempt: Int): String? {
        if (attempt < 1 || attempt > MAX_ATTEMPTS) return null
        val suffix = when (attempt) {
            1 -> "_"
            2 -> "__"
            else -> "_${attempt - 2}"
        }
        return base.take(MAX_NICK_LENGTH - suffix.length) + suffix
    }
}
