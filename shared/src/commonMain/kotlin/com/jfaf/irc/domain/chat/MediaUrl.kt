package com.jfaf.irc.domain.chat

/** Utilidades puras sobre las URL de medios de los mensajes. */
object MediaUrl {

    /** Si la URL apunta a un GIF por su extensión; se ignoran la consulta (`?w=200`) y el fragmento (`#x`). */
    fun isGif(url: String): Boolean {
        val path = url.substringBefore('#').substringBefore('?')
        return path.endsWith(".gif", ignoreCase = true)
    }
}
