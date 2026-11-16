package com.jfaf.irc.domain.channel

/** Orden de la lista de canales. */
enum class ChannelListSort { BY_USERS, BY_NAME }

/** Buscar y ordenar la lista de canales del servidor. Lógica pura, sin interfaz. */
object ChannelListSearch {

    /**
     * Quita los canales señuelo: algunas redes (IRC-Hispano) rellenan la respuesta a `LIST` con canales falsos,
     * de tema «Fake channel for confusing spambots» y miles de usuarios, para despistar a los bots de spam.
     * Se reconocen por el tema (ya sin los códigos de color, [plainTopic]).
     */
    fun withoutDecoys(entries: List<ChannelListEntry>, plainTopic: (String) -> String = { it }): List<ChannelListEntry> =
        entries.filterNot { isDecoy(it, plainTopic) }

    fun isDecoy(entry: ChannelListEntry, plainTopic: (String) -> String = { it }): Boolean =
        plainTopic(entry.topic).contains(DECOY_TOPIC_MARK, ignoreCase = true)

    private const val DECOY_TOPIC_MARK = "fake channel for confusing spambots"

    /**
     * Canales que contienen [query] en el nombre o en el tema, sin distinguir mayúsculas ni el `#`
     * inicial, ordenados según [sort] (por usuarios: de más a menos y, a igualdad, por nombre).
     * Los códigos de color del tema no cuentan: se busca en el texto limpio ([plainTopic]).
     */
    fun filterAndSort(
        entries: List<ChannelListEntry>,
        query: String,
        sort: ChannelListSort,
        plainTopic: (String) -> String = { it }
    ): List<ChannelListEntry> {
        val needle = query.trim().removePrefix("#").lowercase()
        val matching = if (needle.isEmpty()) entries else entries.filter {
            it.name.lowercase().contains(needle) || plainTopic(it.topic).lowercase().contains(needle)
        }
        return when (sort) {
            ChannelListSort.BY_USERS -> matching.sortedWith(
                compareByDescending<ChannelListEntry> { it.users }.thenBy { it.name.lowercase() }
            )
            ChannelListSort.BY_NAME -> matching.sortedBy { it.name.lowercase() }
        }
    }
}
