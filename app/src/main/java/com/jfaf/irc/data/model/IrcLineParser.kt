package com.jfaf.irc.data.model

/** Parser de líneas IRC (RFC 2812 §2.3.1). Tolerante: nunca lanza excepciones. */
object IrcLineParser {

    /** Devuelve null si la línea está vacía o es inválida (el llamador la descarta). */
    fun parse(rawLine: String): ParsedIrcMessage? {
        var rest = rawLine
        if (rest.isBlank()) return null

        // Tags IRCv3 ("@k=v;k2=v2 "): se ignoran, pero rawLine conserva la línea original.
        if (rest.startsWith("@")) {
            val tagsEnd = rest.indexOf(' ')
            if (tagsEnd == -1) return null
            rest = rest.substring(tagsEnd + 1).trimStart(' ')
        }

        var prefix: String? = null
        if (rest.startsWith(":")) {
            val prefixEnd = rest.indexOf(' ')
            if (prefixEnd == -1) return null // prefijo sin comando
            prefix = rest.substring(1, prefixEnd)
            rest = rest.substring(prefixEnd + 1).trimStart(' ')
        }

        var trailing: String? = null
        val trailingStart = rest.indexOf(" :")
        if (trailingStart != -1) {
            trailing = rest.substring(trailingStart + 2)
            rest = rest.substring(0, trailingStart)
        }

        val parts = rest.split(' ').filter { it.isNotEmpty() }
        val command = parts.firstOrNull() ?: return null

        return ParsedIrcMessage(
            rawLine = rawLine,
            prefix = prefix,
            command = command,
            params = parts.drop(1),
            trailing = trailing
        )
    }
}
