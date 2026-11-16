package com.jfaf.irc.domain.chat

/**
 * Selector rápido (Ctrl+K, UI-30): busca entre las pestañas abiertas, los amigos conectados y, si lo escrito parece un
 * canal o un nick, ofrece unirse o escribir. Lógica pura y con tests.
 */
object QuickSwitcher {

    const val MAX_RESULTS = 8

    sealed interface Entry {
        /** Una pestaña abierta (el servidor, un canal o un privado) con lo que tiene sin leer. */
        data class Tab(val target: String, val unread: Boolean, val mentions: Int, val unreadMessages: Int) : Entry

        /** Escribir a un nick sin pestaña abierta: un amigo conectado o un nick tecleado. */
        data class OpenPrivate(val nick: String, val isFriend: Boolean) : Entry

        /** Unirse a un canal que no está entre las pestañas. */
        data class JoinChannel(val channel: String) : Entry
    }

    private class Candidate(val score: Int, val priority: Int, val order: Int, val entry: Entry)

    /**
     * Resultados para [query]. Sin texto, las pestañas ordenadas por urgencia (menciones, privados sin leer, sin leer y
     * el resto en su orden). Con texto, por cercanía: igual, empieza por, empieza una palabra, contiene. La pestaña
     * [active] no sale (ya estás en ella). Un `#` al principio limita la búsqueda a canales. Los canales de
     * [mutedChannels] no suben por sin leer ni por menciones.
     */
    fun search(
        targets: List<String>,
        active: String?,
        unreadTargets: Set<String>,
        mentionCounts: Map<String, Int>,
        pmCounts: Map<String, Int>,
        onlineFriends: Set<String>,
        query: String,
        /** Canales silenciados (UI-32, en minúsculas): no cuentan como sin leer ni como menciones. */
        mutedChannels: Set<String> = emptySet()
    ): List<Entry> {
        val typed = query.trim()
        val channelsOnly = typed.startsWith("#")
        val needle = typed.trimStart('#').lowercase()
        val distinct = targets.distinct()

        val candidates = mutableListOf<Candidate>()
        distinct.forEachIndexed { index, target ->
            if (target.equals(active, ignoreCase = true)) return@forEachIndexed
            if (channelsOnly && !target.startsWith("#")) return@forEachIndexed
            val score = score(target.trimStart('#'), needle) ?: return@forEachIndexed
            // Un canal silenciado no avisa de nada, tampoco aquí: queda con el orden normal, sin insignias
            val muted = target.lowercase() in mutedChannels
            val mentions = if (muted) 0 else mentionCounts.countFor(target)
            val messages = pmCounts.countFor(target)
            val unread = !muted && unreadTargets.any { it.equals(target, ignoreCase = true) } || mentions > 0 || messages > 0
            val priority = when {
                mentions > 0 -> 0
                messages > 0 -> 1
                unread -> 2
                else -> 3
            }
            candidates += Candidate(score, priority, index, Entry.Tab(target, unread, mentions, messages))
        }
        if (!channelsOnly) {
            onlineFriends.sortedBy { it.lowercase() }.forEachIndexed { index, friend ->
                if (distinct.any { it.equals(friend, ignoreCase = true) } || friend.equals(active, ignoreCase = true)) return@forEachIndexed
                val score = score(friend, needle) ?: return@forEachIndexed
                candidates += Candidate(score, 4, distinct.size + index, Entry.OpenPrivate(friend, isFriend = true))
            }
        }

        val results = candidates
            .sortedWith(compareBy<Candidate> { it.score }.thenBy { it.priority }.thenBy { it.order })
            .take(MAX_RESULTS)
            .map { it.entry }
            .toMutableList()

        // Lo escrito, si no existe ya, se ofrece como acción: unirse a un canal o escribir a un nick
        if (isChannelName(typed) && distinct.none { it.equals(typed, ignoreCase = true) }) {
            results += Entry.JoinChannel(typed)
        } else if (isNickLike(typed) &&
            distinct.none { it.equals(typed, ignoreCase = true) } &&
            onlineFriends.none { it.equals(typed, ignoreCase = true) } &&
            !typed.equals(active, ignoreCase = true)
        ) {
            results += Entry.OpenPrivate(typed, isFriend = false)
        }
        return results
    }

    /** `#nombre` sin espacios, comas ni caracteres de control. */
    fun isChannelName(text: String): Boolean =
        text.length > 1 && text.startsWith("#") && text.drop(1).none { it.isWhitespace() || it == ',' || it == '#' || it.code < 32 }

    /** Un nick posible: una palabra sin `#`, espacios ni comas (el caso de uso valida el resto al abrirlo). */
    fun isNickLike(text: String): Boolean =
        text.isNotEmpty() && text.length <= 30 && text.none { it.isWhitespace() || it == ',' || it == '#' || it.code < 32 }

    /** 0 = igual, 1 = empieza por, 2 = empieza una palabra, 3 = contiene; null si no coincide. Sin texto, todo vale. */
    private fun score(name: String, needle: String): Int? {
        if (needle.isEmpty()) return 0
        val lower = name.lowercase()
        return when {
            lower == needle -> 0
            lower.startsWith(needle) -> 1
            lower.split('-', '_', '.', ' ').any { it.startsWith(needle) } -> 2
            lower.contains(needle) -> 3
            else -> null
        }
    }

    private fun Map<String, Int>.countFor(target: String): Int =
        entries.firstOrNull { it.key.equals(target, ignoreCase = true) }?.value ?: 0
}
