package com.jfaf.irc.domain.chat

/** Navegación entre pestañas con el teclado (Ctrl+Tab y Ctrl+Mayús+Tab): lógica pura y con tests. */
object TargetNavigation {

    /**
     * Pestaña a la que se pasa desde [active]: la siguiente si [forward], la anterior si no. Es circular.
     * Sin pestaña activa (o si ya no está en la lista) se empieza por el principio o por el final.
     * Devuelve `null` si no hay otra pestaña a la que ir.
     */
    fun neighbour(targets: List<String>, active: String?, forward: Boolean): String? {
        val distinct = targets.distinct()
        if (distinct.size < 2 && active in distinct) return null
        if (distinct.isEmpty()) return null
        val index = distinct.indexOf(active)
        val next = when {
            index < 0 -> if (forward) 0 else distinct.lastIndex
            forward -> (index + 1) % distinct.size
            else -> (index - 1 + distinct.size) % distinct.size
        }
        return distinct[next]
    }
}
