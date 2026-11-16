package com.jfaf.irc.domain.user

/**
 * Respuesta de un WHOIS ya agrupada. [found] es false si el usuario no existe (401) o el servidor
 * cerró la consulta sin datos; en ese caso sólo [nick] es significativo.
 */
data class WhoisResult(
    val nick: String,
    val found: Boolean,
    val user: String? = null,
    val host: String? = null,
    val realName: String? = null,
    val server: String? = null,
    val serverInfo: String? = null,
    val idleSeconds: Long? = null,
    /** Momento de conexión, en segundos Unix. */
    val signOnEpochSeconds: Long? = null,
    /** Canales tal como los manda el servidor, con su prefijo de estado (`@#canal`, `+#canal`). */
    val channels: List<String> = emptyList(),
    val account: String? = null,
    val isOperator: Boolean = false,
    val isSecure: Boolean = false,
    val awayMessage: String? = null,
    /** Host real cuando el servidor lo revela a quien pregunta (378). */
    val actualHost: String? = null
)

/** Nombre de canal sin los prefijos de estado (`@`, `+`, `%`, `&`, `~`) que antepone el servidor. */
fun String.channelWithoutStatusPrefix(): String = dropWhile { it in "@+%&~" }

/** "45 s", "3 min", "2 h 5 min", "1 d 4 h": sólo las dos unidades mayores. */
fun formatCompactDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val days = s / 86_400
    val hours = (s % 86_400) / 3_600
    val minutes = (s % 3_600) / 60
    return when {
        days > 0 -> if (hours > 0) "$days d $hours h" else "$days d"
        hours > 0 -> if (minutes > 0) "$hours h $minutes min" else "$hours h"
        minutes > 0 -> "$minutes min"
        else -> "$s s"
    }
}
