package com.jfaf.irc.domain.chat

/** Qué hay que escribir/borrar en el almacén para que refleje el estado actual. */
data class HistoryChanges<T>(val replace: Map<String, List<T>>, val delete: Set<String>) {
    val isEmpty: Boolean get() = replace.isEmpty() && delete.isEmpty()
}

object HistoryDiff {

    /** Compara el último estado guardado con el actual; [excluded] (p. ej. la pestaña Servidor) no se guarda nunca. */
    fun <T> compute(last: Map<String, List<T>>, current: Map<String, List<T>>, excluded: String): HistoryChanges<T> {
        val replace = current.filter { (target, msgs) -> target != excluded && last[target] != msgs }
        val delete = last.keys.filter { it != excluded && it !in current }.toSet()
        return HistoryChanges(replace, delete)
    }
}
