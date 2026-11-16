package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.chat.command.ChatCommands

/**
 * Autocompletado del campo de mensajes (UI-36): según la palabra que hay bajo el cursor, sugiere
 *  - **comandos** (`/jo` → `/join`), si es la primera palabra del texto y empieza por `/`;
 *  - **canales** (`#ko` → `#kotlin`), si empieza por `#`: primero los abiertos y luego los de la última lista del
 *    explorador de canales;
 *  - **nicks** del canal (el de siempre, [NickAutocomplete]), en el resto de los casos.
 *
 * Elegir una sugerencia sustituye la palabra entera y añade un espacio ([NickAutocomplete.apply]). Lógica pura.
 */
object InputAutocomplete {

    const val MAX_SUGGESTIONS = NickAutocomplete.MAX_SUGGESTIONS

    /**
     * @param knownChannels los canales que se pueden sugerir, en orden de preferencia; sólo se pide cuando la palabra
     * empieza por `#` (puede ser una lista larga).
     */
    fun suggestions(
        activeTarget: String?,
        usersInChannel: List<String>,
        knownChannels: () -> List<String>,
        text: String,
        cursorPosition: Int,
        ownNick: String? = null
    ): List<String> {
        val cursor = cursorPosition.coerceIn(0, text.length)
        val wordStart = text.substring(0, cursor).lastIndexOf(' ') + 1
        val word = text.substring(wordStart, cursor)
        return when {
            wordStart == 0 && word.startsWith("/") -> commands(word)
            word.startsWith("#") -> channels(word, knownChannels())
            else -> NickAutocomplete.suggestions(activeTarget, usersInChannel, text, cursor, ownNick)
        }
    }

    /** Un `/` solo ya sugiere (los comandos más usados); no se sugiere el que ya está escrito entero. */
    private fun commands(word: String): List<String> {
        val prefix = word.drop(1).lowercase()
        return ChatCommands.NAMES
            .filter { it.startsWith(prefix) && it.length > prefix.length }
            .map { "/$it" }
            .take(MAX_SUGGESTIONS)
    }

    /** Hace falta algo más que el `#`: con una sola letra ya hay de qué escoger. Sin repetidos, sin distinguir mayúsculas. */
    private fun channels(word: String, known: List<String>): List<String> {
        if (word.length < 2) return emptyList()
        val found = LinkedHashMap<String, String>()
        for (channel in known) {
            if (found.size >= MAX_SUGGESTIONS) break
            if (channel.length > word.length && channel.startsWith(word, ignoreCase = true)) {
                found.putIfAbsent(channel.lowercase(), channel)
            }
        }
        return found.values.toList()
    }
}
