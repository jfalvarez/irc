package com.jfaf.irc.domain.channel

/**
 * Un canal de la lista del servidor (respuesta 322 de `LIST`).
 *
 * @param users usuarios dentro del canal, según el servidor.
 * @param modes modos del canal sin el `+` (`nt`, `ntk`…); vacío si el servidor no los manda.
 * @param topic tema tal como llega, con los códigos de color de mIRC si los lleva.
 */
data class ChannelListEntry(
    val name: String,
    val users: Int,
    val modes: String = "",
    val topic: String = ""
) {
    /** El canal pide clave (`+k`): unirse sin ella fallará. */
    val hasKey: Boolean get() = 'k' in modes
}

/** Cómo terminó una consulta `LIST`. */
enum class ChannelListStatus {
    /** El servidor mandó el cierre (323): la lista está completa. */
    COMPLETE,

    /** No llegó el cierre a tiempo: la lista puede estar incompleta. */
    TIMED_OUT,

    /** El servidor se negó (263 «inténtalo más tarde» o 416 «demasiados resultados»). */
    REFUSED
}

data class ChannelListResult(
    val entries: List<ChannelListEntry>,
    val status: ChannelListStatus
)
