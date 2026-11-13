package com.jfaf.irc.domain.chat

/** Enlace encontrado en un texto: posición [start, end) y destino ya con esquema. */
data class FoundLink(val start: Int, val end: Int, val text: String, val target: String)

/** Detecta enlaces en texto de chat (sustituye a android.util.Patterns.WEB_URL). */
object LinkFinder {
    // http/https/ftp con esquema | www.… | dominio (también IDN) con puerto y ruta opcionales | IPv4
    private val urlRegex = Regex(
        """\b(?:(?:https?|ftp)://[^\s<>"]+|www\.[^\s<>"]+|(?:[\p{L}\p{N}-]+\.)+\p{L}{2,}(?::\d{1,5})?(?:/[^\s<>"]*)?|\d{1,3}(?:\.\d{1,3}){3}(?::\d{1,5})?(?:/[^\s<>"]*)?)""",
        RegexOption.IGNORE_CASE
    )
    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private const val TRAILING_PUNCTUATION = ".,;:!?)]\"'"

    fun find(text: String): List<FoundLink> = urlRegex.findAll(text).mapNotNull { m ->
        val url = m.value.trimEnd(*TRAILING_PUNCTUATION.toCharArray())
        if (url.isEmpty()) null
        else FoundLink(
            start = m.range.first,
            end = m.range.first + url.length,
            text = url,
            target = if (schemeRegex.containsMatchIn(url)) url else "https://$url"
        )
    }.toList()
}
