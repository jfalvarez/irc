package com.jfaf.irc.domain.chat

import com.jfaf.irc.domain.chat.model.UiMessageType

/**
 * Búsqueda dentro del chat de la pestaña activa (UI-35). Lógica pura, sin interfaz.
 *
 * Se busca en los mensajes que hay en la línea de tiempo (lo que se ve en pantalla, que es lo mismo que se guarda en
 * el historial: el filtro de entradas y salidas del usuario también vale aquí). Las entradas y salidas plegadas en
 * una sola línea ([TimelineItem.PresenceGroup]) no se buscan.
 */
object ChatSearch {

    /**
     * Texto comparable: en minúsculas y sin acentos (`música` casa con `musica`). Conserva la `ñ`, que es otra letra,
     * y la longitud (cada carácter se sustituye por uno).
     */
    fun fold(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (val l = c.lowercaseChar()) {
                    'á', 'à', 'â', 'ä' -> 'a'
                    'é', 'è', 'ê', 'ë' -> 'e'
                    'í', 'ì', 'î', 'ï' -> 'i'
                    'ó', 'ò', 'ô', 'ö' -> 'o'
                    'ú', 'ù', 'û', 'ü' -> 'u'
                    else -> l
                }
            )
        }
    }

    /**
     * Posiciones (en [items], en orden cronológico) de los mensajes que contienen [query], **del más reciente al más
     * antiguo**. Con la consulta vacía o en blanco no hay resultados. El texto es el que se ve (sin los códigos de
     * color de mIRC) y el nick de quien lo dijo forma parte de él (`<ana> hola`).
     */
    fun matches(items: List<TimelineItem>, query: String): List<Int> {
        val needle = fold(query.trim())
        if (needle.isEmpty()) return emptyList()
        val found = ArrayList<Int>()
        items.forEachIndexed { index, item ->
            if (item !is TimelineItem.Message || item.message.type == UiMessageType.AD_MESSAGE) return@forEachIndexed
            val text = item.message.annotatedString?.text ?: item.message.fullText
            if (fold(text).contains(needle)) found += index
        }
        found.reverse()
        return found
    }

    /**
     * El resultado al que se pasa desde [current] (0 = el más reciente): [towardsOlder] avanza hacia los más
     * antiguos y, al llegar al final, da la vuelta. Con [count] 0 no hay a dónde ir.
     */
    fun step(count: Int, current: Int, towardsOlder: Boolean): Int {
        if (count <= 0) return 0
        val from = current.coerceIn(0, count - 1)
        return if (towardsOlder) (from + 1) % count else (from - 1 + count) % count
    }
}
