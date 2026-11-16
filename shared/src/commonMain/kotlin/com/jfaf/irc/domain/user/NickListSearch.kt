package com.jfaf.irc.domain.user

/**
 * Búsqueda sobre una lista de nicks (ignorados, amigos). Lógica pura: la pantalla ordena una vez
 * por cambio de la lista y filtra por cada cambio del texto, sin recorrer nada más.
 */
object NickListSearch {

    /** Orden estable e insensible a mayúsculas (los nicks se guardan ya en minúsculas). */
    fun sorted(nicks: Set<String>): List<String> = nicks.sortedBy { it.lowercase() }

    /** Los nicks de [sortedNicks] que contienen [query] (sin distinguir mayúsculas); sin texto, todos. */
    fun filter(sortedNicks: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return sortedNicks
        return sortedNicks.filter { it.contains(q, ignoreCase = true) }
    }

    /**
     * El nick que se añadiría con el texto escrito, o null si no procede: vacío, con espacios
     * (un nick no los tiene) o ya presente en [existing] (que está en minúsculas).
     */
    fun nickToAdd(query: String, existing: Set<String>): String? {
        val nick = query.trim()
        if (nick.isEmpty() || nick.any { it.isWhitespace() }) return null
        return nick.takeUnless { it.lowercase() in existing }
    }
}
