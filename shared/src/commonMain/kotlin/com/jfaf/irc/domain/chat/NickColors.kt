package com.jfaf.irc.domain.chat

/**
 * Reglas puras de la identidad visual de un nick (UI-4 y UI-9): qué color le toca y qué inicial lleva el avatar.
 * El mismo nick da siempre el mismo índice, sin distinguir mayúsculas ni el prefijo de modo (`@`, `+`…),
 * así que el color es el mismo en el mensaje, el avatar y la lista de usuarios.
 */
object NickColors {
    /** Número de colores de la paleta (`NickPalette`, en la UI). */
    const val PALETTE_SIZE = 12

    private const val MODE_PREFIXES = "@+%&~"

    /** El nick sin el prefijo de modo de canal. */
    fun normalize(nick: String): String = nick.trimStart { it in MODE_PREFIXES }

    /** Índice estable en la paleta: FNV-1a sobre el nick normalizado en minúsculas. */
    fun paletteIndex(nick: String, paletteSize: Int = PALETTE_SIZE): Int {
        require(paletteSize > 0)
        var hash = 2166136261L
        for (c in normalize(nick).lowercase()) {
            hash = (hash xor c.code.toLong()) and 0xFFFFFFFFL
            hash = (hash * 16777619L) and 0xFFFFFFFFL
        }
        return (hash % paletteSize).toInt()
    }

    /** Inicial en mayúscula para el avatar: la primera letra o cifra; si no hay, el primer carácter; `?` si el nick está vacío. */
    fun initial(nick: String): String {
        val clean = normalize(nick)
        if (clean.isEmpty()) return "?"
        val first = clean.firstOrNull { it.isLetterOrDigit() } ?: clean[0]
        return first.uppercaseChar().toString()
    }
}
