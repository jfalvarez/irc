package com.jfaf.irc.domain.chat

/** Lógica pura para meter un emoticono en el campo de texto. */
object EmojiInsertion {

    /** El texto resultante y dónde queda el cursor (justo tras el emoticono). */
    class Result(val text: String, val cursor: Int)

    /**
     * Inserta [emoji] en la posición del cursor, o sustituye lo seleccionado si hay selección
     * ([selectionStart] y [selectionEnd] pueden venir en cualquier orden y se acotan al texto).
     */
    fun insert(text: String, selectionStart: Int, selectionEnd: Int, emoji: String): Result {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        return Result(text.substring(0, start) + emoji + text.substring(end), start + emoji.length)
    }
}

/** Los emoticonos usados últimamente: el más reciente primero, sin repetir y con un máximo. */
object RecentEmojis {

    const val MAX = 24

    fun add(recents: List<String>, emoji: String, max: Int = MAX): List<String> =
        (listOf(emoji) + recents.filterNot { it == emoji }).take(max)

    /** Para guardarlos en una sola cadena: ningún emoticono contiene espacios, que los separan. */
    fun encode(recents: List<String>): String = recents.joinToString(" ")

    fun decode(stored: String?): List<String> =
        stored.orEmpty().split(' ').filter { it.isNotBlank() }.distinct().take(MAX)
}
