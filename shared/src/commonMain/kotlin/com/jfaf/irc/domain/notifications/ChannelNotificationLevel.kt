package com.jfaf.irc.domain.notifications

import kotlinx.coroutines.flow.Flow

/**
 * Cuánto avisa un canal con la app en segundo plano (UI-32). Por defecto, sólo las menciones del propio nick: antes
 * los canales no avisaban de nada. El nivel por defecto se puede cambiar en Ajustes (UI-34).
 */
enum class ChannelNotificationLevel {
    /** Cada mensaje del canal. */
    ALL,

    /** Sólo cuando alguien nombra tu nick. */
    MENTIONS,

    /** Nada. */
    MUTED;

    companion object {
        /** El nivel por defecto de fábrica; el usuario lo puede cambiar en Ajustes (UI-34). */
        val Default = MENTIONS

        /** Lee el valor guardado; lo desconocido o ausente da el predeterminado. */
        fun fromStorage(value: String?): ChannelNotificationLevel = entries.firstOrNull { it.name == value } ?: Default
    }
}

/**
 * Dónde se guarda el nivel de cada canal. La clave es el nombre del canal en minúsculas; un canal que no está en el mapa
 * sigue el nivel por defecto ([defaultLevelFlow]), que cambia en Ajustes. Lo implementa `UserPreferencesRepository`
 * (DataStore).
 */
interface ChannelNotificationStore {
    /** Los niveles propios de cada canal (sólo los que el usuario ha elegido). */
    val levelsFlow: Flow<Map<String, ChannelNotificationLevel>>

    /** El nivel de los canales sin uno propio. */
    val defaultLevelFlow: Flow<ChannelNotificationLevel>

    /** Da a [channel] un nivel propio; con null vuelve a seguir el nivel por defecto. */
    suspend fun setLevel(channel: String, level: ChannelNotificationLevel?)

    suspend fun setDefaultLevel(level: ChannelNotificationLevel)
}

/** Lectura y escritura del mapa de niveles por canal, y su forma guardada. Lógica pura y con tests. */
object ChannelNotificationLevels {

    /** El nivel de [channel] (sin distinguir mayúsculas): el suyo, o [default] si no ha elegido uno. */
    fun levelFor(
        levels: Map<String, ChannelNotificationLevel>,
        channel: String,
        default: ChannelNotificationLevel = ChannelNotificationLevel.Default
    ): ChannelNotificationLevel = levels[channel.lowercase()] ?: default

    /** Los canales de [targets] (sólo `#…`, en minúsculas) cuyo nivel es «Silenciado», con su nivel o el [default]. */
    fun mutedAmong(
        targets: Collection<String>,
        levels: Map<String, ChannelNotificationLevel>,
        default: ChannelNotificationLevel
    ): Set<String> = targets
        .filter { it.startsWith("#") }
        .map { it.lowercase() }
        .filter { levelFor(levels, it, default) == ChannelNotificationLevel.MUTED }
        .toSet()

    /**
     * El mapa con [channel] en [level]; con null se quita y el canal vuelve a seguir el nivel por defecto. Un nivel
     * elegido se guarda aunque coincida con el de ahora: así no cambia solo si luego cambia el nivel por defecto.
     */
    fun with(levels: Map<String, ChannelNotificationLevel>, channel: String, level: ChannelNotificationLevel?): Map<String, ChannelNotificationLevel> {
        val key = channel.lowercase()
        return if (level == null) levels - key else levels + (key to level)
    }

    /** Un texto por canal: `NIVEL:canal`. El nivel no lleva `:`, así que lo que sigue es el nombre entero. */
    fun encode(levels: Map<String, ChannelNotificationLevel>): Set<String> =
        levels.map { (channel, level) -> "${level.name}:$channel" }.toSet()

    /** Lo contrario de [encode]; lo mal formado o de un nivel desconocido se ignora. */
    fun decode(stored: Set<String>?): Map<String, ChannelNotificationLevel> = buildMap {
        stored.orEmpty().forEach { entry ->
            val level = ChannelNotificationLevel.entries.firstOrNull { entry.startsWith(it.name + ":") } ?: return@forEach
            val channel = entry.substringAfter(':').lowercase()
            if (channel.startsWith("#")) put(channel, level)
        }
    }
}

/** Sin ningún canal tocado: todos con el nivel predeterminado. */
object NoChannelNotificationLevels : ChannelNotificationStore {
    override val levelsFlow: Flow<Map<String, ChannelNotificationLevel>> = kotlinx.coroutines.flow.flowOf(emptyMap())
    override val defaultLevelFlow: Flow<ChannelNotificationLevel> = kotlinx.coroutines.flow.flowOf(ChannelNotificationLevel.Default)
    override suspend fun setLevel(channel: String, level: ChannelNotificationLevel?) {}
    override suspend fun setDefaultLevel(level: ChannelNotificationLevel) {}
}
