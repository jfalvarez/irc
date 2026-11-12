package com.jfaf.irc.ui.viewmodels

/** Lógica pura del autocompletado de nicks en el campo de entrada. */
object NickAutocomplete {

    const val MAX_SUGGESTIONS = 5

    /** Sugerencias para la palabra bajo el cursor; vacío si el destino no es un canal o la palabra es corta. */
    fun suggestions(
        activeTarget: String?,
        usersInChannel: List<String>,
        text: String,
        cursorPosition: Int
    ): List<String> {
        if (activeTarget == null || !activeTarget.startsWith("#")) return emptyList()

        val textUpToCursor = text.substring(0, cursorPosition)
        val currentWord = textUpToCursor.substring(textUpToCursor.lastIndexOf(' ') + 1)
        if (currentWord.length < 2 && !currentWord.startsWith("/")) return emptyList()

        return usersInChannel
            .filter { it.startsWith(currentWord, ignoreCase = true) && it.length > currentWord.length }
            .take(MAX_SUGGESTIONS)
    }

    /** Sustituye la palabra bajo el cursor por [suggestion] y añade un espacio. */
    fun apply(suggestion: String, text: String, cursorPosition: Int): String {
        val textUpToCursor = text.substring(0, cursorPosition)
        val wordStart = textUpToCursor.lastIndexOf(' ') + 1
        val prefix = text.substring(0, wordStart)
        val suffix = text.substring(cursorPosition)
        return "$prefix$suggestion ${suffix.trimStart()}"
    }
}
