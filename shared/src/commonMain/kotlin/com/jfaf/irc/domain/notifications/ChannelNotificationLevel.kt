package com.jfaf.irc.domain.notifications

import kotlinx.coroutines.flow.Flow

/**
 * Cuánto avisa un canal con la app en segundo plano (UI-32). Por defecto, sólo las menciones del propio nick: antes
 * los canales no avisaban de nada.
 */
enum class ChannelNotificationLevel {
    /** Cada mensaje del canal. */
    ALL,

    /** Sólo cuando alguien nombra tu nick. */
    MENTIONS,

    /** Nada. */
    MUTED;

    companion object {
        val Default = MENTIONS

        /** Lee el valor guardado; lo desconocido o ausente da el predeterminado. */
        fun fromStorage(value: String?): ChannelNotificationLevel = entries.firstOrNull { it.name == value } ?: Default
    }
}

/**
 * Dónde se guarda el nivel de cada canal. La clave es el nombre del canal en minúsculas; un canal que no está en el mapa
 * tiene el nivel por defecto. Lo implementa `UserPreferencesRepository` (DataStore).
 */
interface ChannelNotificationStore {
    val levelsFlow: Flow<Map<String, ChannelNotificationLevel>>
    suspend fun setLevel(channel: String, level: ChannelNotificationLevel)
}

/** Lectura y escritura del mapa de niveles por canal, y su forma guardada. Lógica pura y con tests. */
object ChannelNotificationLevels {

    /** El nivel de [channel] (sin distinguir mayúsculas); el predeterminado si no se ha tocado. */
    fun levelFor(levels: Map<String, ChannelNotificationLevel>, channel: String): ChannelNotificationLevel =
        levels[channel.lowercase()] ?: ChannelNotificationLevel.Default

    /** El mapa con [channel] en [level]; si es el predeterminado se quita, para no guardar lo que no cambia nada. */
    fun with(levels: Map<String, ChannelNotificationLevel>, channel: String, level: ChannelNotificationLevel): Map<String, ChannelNotificationLevel> {
        val key = channel.lowercase()
        return if (level == ChannelNotificationLevel.Default) levels - key else levels + (key to level)
    }

    /** Un texto por canal: `NIVEL:canal`. El nivel no lleva `:`, así que lo que sigue es el nombre entero. */
    fun encode(levels: Map<String, ChannelNotificationLevel>): Set<String> =
        levels.filterValues { it != ChannelNotificationLevel.Default }.map { (channel, level) -> "${level.name}:$channel" }.toSet()

    /** Lo contrario de [encode]; lo mal formado o de un nivel desconocido se ignora. */
    fun decode(stored: Set<String>?): Map<String, ChannelNotificationLevel> = buildMap {
        stored.orEmpty().forEach { entry ->
            val level = ChannelNotificationLevel.entries.firstOrNull { entry.startsWith(it.name + ":") } ?: return@forEach
            val channel = entry.substringAfter(':').lowercase()
            if (channel.startsWith("#") && level != ChannelNotificationLevel.Default) put(channel, level)
        }
    }
}

/** Sin ningún canal tocado: todos con el nivel predeterminado. */
object NoChannelNotificationLevels : ChannelNotificationStore {
    override val levelsFlow: Flow<Map<String, ChannelNotificationLevel>> = kotlinx.coroutines.flow.flowOf(emptyMap())
    override suspend fun setLevel(channel: String, level: ChannelNotificationLevel) {}
}
