package com.jfaf.irc.domain.chat

/** Lógica pura del autocompletado de nicks en el campo de entrada. */
object NickAutocomplete {

    const val MAX_SUGGESTIONS = 5

    /** Prefijos de modo con los que `NAMES` entrega los nicks (`@op`, `+voz`, y con multi-prefix `~&%`). */
    private const val MODE_PREFIXES = "~&@%+"

    /**
     * Sugerencias para la palabra bajo el cursor; vacío si el destino no es un canal o la palabra es corta.
     *
     * [usersInChannel] puede traer los nicks con su prefijo de modo: se comparan y se sugieren sin él (el prefijo no
     * forma parte del nick). Si la palabra empieza por `@` (mención) se ignora al comparar. [ownNick] no se sugiere.
     */
    fun suggestions(
        activeTarget: String?,
        usersInChannel: List<String>,
        text: String,
        cursorPosition: Int,
        ownNick: String? = null
    ): List<String> {
        if (activeTarget == null || !activeTarget.startsWith("#")) return emptyList()

        val currentWord = wordBeforeCursor(text, cursorPosition).removePrefix("@")
        if (currentWord.length < 2) return emptyList()

        return usersInChannel
            .map { it.trimStart { c -> c in MODE_PREFIXES } }
            .filter { it.isNotEmpty() && !it.equals(ownNick, ignoreCase = true) }
            .filter { it.startsWith(currentWord, ignoreCase = true) && it.length > currentWord.length }
            .distinct()
            .take(MAX_SUGGESTIONS)
    }

    /**
     * Sustituye la palabra bajo el cursor (entera, también lo que queda tras el cursor) por [suggestion] y añade un
     * espacio. Si la palabra era una mención (`@al`), se conserva la `@`.
     */
    fun apply(suggestion: String, text: String, cursorPosition: Int): String {
        val wordStart = wordStart(text, cursorPosition)
        val prefix = text.substring(0, wordStart)
        val suffix = text.substring(wordEnd(text, cursorPosition))
        return "$prefix${mention(text, wordStart)}$suggestion ${suffix.trimStart()}"
    }

    /** Posición del cursor tras aplicar [suggestion]: justo después del espacio que se añade. */
    fun cursorAfterApply(suggestion: String, text: String, cursorPosition: Int): Int {
        val wordStart = wordStart(text, cursorPosition)
        return wordStart + mention(text, wordStart).length + suggestion.length + 1
    }

    private fun wordStart(text: String, cursorPosition: Int) = text.substring(0, cursorPosition).lastIndexOf(' ') + 1

    private fun wordEnd(text: String, cursorPosition: Int) =
        text.indexOf(' ', cursorPosition).let { if (it == -1) text.length else it }

    private fun mention(text: String, wordStart: Int) = if (text.startsWith("@", wordStart)) "@" else ""

    private fun wordBeforeCursor(text: String, cursorPosition: Int) =
        text.substring(wordStart(text, cursorPosition), cursorPosition)
}
