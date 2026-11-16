package com.jfaf.irc.data.prefs

import com.jfaf.irc.domain.chat.RecentEmojis
import com.jfaf.irc.log.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jfaf.irc.domain.notifications.ChannelNotificationLevel
import com.jfaf.irc.domain.notifications.ChannelNotificationLevels
import com.jfaf.irc.domain.notifications.ChannelNotificationStore
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import okio.IOException

data class UserPreferences(val nickname: String, val nickServPassword: String)

/**
 * Previsualizaciones de imágenes y vídeos desactivadas si el usuario nunca tocó la opción: en un chat
 * abierto cualquiera puede enlazar una imagen explícita.
 */
const val DEFAULT_SHOW_MEDIA_PREVIEWS = false
/** Vista previa de los enlaces (UI-31): apagada por defecto, porque visitar el enlace de un desconocido le revela tu IP. */
const val DEFAULT_SHOW_LINK_PREVIEWS = false

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) : ChannelNotificationStore {
    private companion object {
        private const val SHOW_JOIN_PART_QUIT_KEY_NAME = "show_join_part_quit"
        private const val SHOW_NICK_CHANGES_KEY_NAME = "show_nick_changes"
        private const val SHOW_MODE_CHANGES_KEY_NAME = "show_mode_changes"
        private const val SHOW_PING_PONG_KEY_NAME = "show_ping_pong_messages"
        private const val NICKSERV_PASSWORD_KEY_NAME = "nickserv_password"
        private const val SHOW_MEDIA_PREVIEWS_KEY_NAME = "show_media_previews"
        private const val NICKNAME_KEY_NAME = "nickname"

        val SHOW_JOIN_PART_QUIT = booleanPreferencesKey(SHOW_JOIN_PART_QUIT_KEY_NAME)
        val SHOW_NICK_CHANGES = booleanPreferencesKey(SHOW_NICK_CHANGES_KEY_NAME)
        val SHOW_MODE_CHANGES = booleanPreferencesKey(SHOW_MODE_CHANGES_KEY_NAME)
        val SHOW_PING_PONG_MESSAGES = booleanPreferencesKey(SHOW_PING_PONG_KEY_NAME)
        val NICKSERV_PASSWORD = stringPreferencesKey(NICKSERV_PASSWORD_KEY_NAME)
        val SHOW_MEDIA_PREVIEWS = booleanPreferencesKey(SHOW_MEDIA_PREVIEWS_KEY_NAME)
        val SHOW_LINK_PREVIEWS = booleanPreferencesKey("show_link_previews")
        val NICKNAME = stringPreferencesKey(NICKNAME_KEY_NAME)

        val SOUND_ALERTS = booleanPreferencesKey("sound_alerts")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MESSAGE_DENSITY = stringPreferencesKey("message_density")
        val CHANNEL_NOTIFICATION_LEVELS = stringSetPreferencesKey("channel_notification_levels")
        val TEXT_SCALE = stringPreferencesKey("text_scale")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val RECENT_EMOJIS = stringPreferencesKey("recent_emojis")
        val TERMS_ACCEPTED_VERSION = intPreferencesKey("terms_accepted_version")

        val SERVER_HOST = stringPreferencesKey("server_host")
        val SERVER_PLAIN_PORT = intPreferencesKey("server_plain_port")
        val SERVER_SSL_PORT = intPreferencesKey("server_ssl_port")

        const val TAG = "UserPrefsRepository"
    }

    val userPreferencesFlow: Flow<UserPreferences> = combine(
        dataStore.data.catch { emit(emptyPreferences()) }.map { it[NICKNAME] ?: "" },
        dataStore.data.catch { emit(emptyPreferences()) }.map { it[NICKSERV_PASSWORD] ?: "" }
    ) { nickname, password ->
        UserPreferences(nickname, password)
    }

    val serverConfigFlow: Flow<ServerConfig> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            ServerConfig(
                host = p[SERVER_HOST] ?: ServerConfig.DEFAULT_HOST,
                plainPort = p[SERVER_PLAIN_PORT] ?: ServerConfig.DEFAULT_PLAIN_PORT,
                sslPort = p[SERVER_SSL_PORT] ?: ServerConfig.DEFAULT_SSL_PORT
            )
        }

    suspend fun updateServerConfig(config: ServerConfig) {
        dataStore.edit { p ->
            p[SERVER_HOST] = config.host
            p[SERVER_PLAIN_PORT] = config.plainPort
            p[SERVER_SSL_PORT] = config.sslPort
        }
    }

    suspend fun resetServerConfig() {
        dataStore.edit { p ->
            p.remove(SERVER_HOST)
            p.remove(SERVER_PLAIN_PORT)
            p.remove(SERVER_SSL_PORT)
        }
    }

    suspend fun updateNickname(nickname: String) {
        dataStore.edit { preferences ->
            preferences[NICKNAME] = nickname
        }
    }

    /** Aspecto de la app: oscuro (por defecto), claro o el del sistema. */
    val themeModeFlow: Flow<ThemeMode> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { ThemeMode.fromStorage(it[THEME_MODE]) }

    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

    /** Densidad de los mensajes: cómoda (por defecto) o compacta. */
    val messageDensityFlow: Flow<MessageDensity> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { MessageDensity.fromStorage(it[MESSAGE_DENSITY]) }

    suspend fun updateMessageDensity(density: MessageDensity) {
        dataStore.edit { preferences ->
            preferences[MESSAGE_DENSITY] = density.name
        }
    }

    /** Tamaño del texto de la app (UI-29): normal por defecto. */
    val textScaleFlow: Flow<TextScale> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { TextScale.fromStorage(it[TEXT_SCALE]) }

    suspend fun updateTextScale(scale: TextScale) {
        dataStore.edit { preferences ->
            preferences[TEXT_SCALE] = scale.name
        }
    }

    /** Negro puro en el tema oscuro (UI-29), para pantallas OLED. Desactivado por defecto. */
    val amoledBlackFlow: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[AMOLED_BLACK] ?: false }

    suspend fun updateAmoledBlack(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AMOLED_BLACK] = enabled
        }
    }

    /** Sonido de aviso de privados y amigos conectados (sólo escritorio; en Android lo decide el sistema). Activado por defecto. */
    val soundAlertsFlow: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[SOUND_ALERTS] ?: true }

    suspend fun updateSoundAlerts(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SOUND_ALERTS] = enabled
        }
    }

    /** Los emoticonos usados últimamente (selector de escritorio): el más reciente primero. */
    val recentEmojisFlow: Flow<List<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { RecentEmojis.decode(it[RECENT_EMOJIS]) }

    suspend fun addRecentEmoji(emoji: String) {
        dataStore.edit { preferences ->
            preferences[RECENT_EMOJIS] = RecentEmojis.encode(RecentEmojis.add(RecentEmojis.decode(preferences[RECENT_EMOJIS]), emoji))
        }
    }

    val showMediaPreviewsFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading SHOW_MEDIA_PREVIEWS preferences.", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SHOW_MEDIA_PREVIEWS] ?: DEFAULT_SHOW_MEDIA_PREVIEWS
        }

    /** Nivel de aviso de cada canal con la app en segundo plano (UI-32); lo no tocado tiene el predeterminado. */
    override val levelsFlow: Flow<Map<String, ChannelNotificationLevel>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { ChannelNotificationLevels.decode(it[CHANNEL_NOTIFICATION_LEVELS]) }

    override suspend fun setLevel(channel: String, level: ChannelNotificationLevel) {
        dataStore.edit { preferences ->
            val updated = ChannelNotificationLevels.with(ChannelNotificationLevels.decode(preferences[CHANNEL_NOTIFICATION_LEVELS]), channel, level)
            if (updated.isEmpty()) preferences.remove(CHANNEL_NOTIFICATION_LEVELS) else preferences[CHANNEL_NOTIFICATION_LEVELS] = ChannelNotificationLevels.encode(updated)
        }
    }

    /** Vista previa de los enlaces de los mensajes (UI-31). */
    val showLinkPreviewsFlow: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[SHOW_LINK_PREVIEWS] ?: DEFAULT_SHOW_LINK_PREVIEWS }

    suspend fun updateShowLinkPreviews(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_LINK_PREVIEWS] = show
        }
    }

    /** Versión de las condiciones de uso que aceptó el usuario; null si nunca (ver `TermsOfUse`). */
    val termsAcceptedVersionFlow: Flow<Int?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[TERMS_ACCEPTED_VERSION] }

    suspend fun acceptTerms(version: Int) {
        dataStore.edit { it[TERMS_ACCEPTED_VERSION] = version }
    }

    suspend fun updateShowMediaPreviews(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_MEDIA_PREVIEWS] = show
        }
    }

    val showJoinPartQuitFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading SHOW_JOIN_PART_QUIT preferences.", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SHOW_JOIN_PART_QUIT] ?: true
        }

    suspend fun updateShowJoinPartQuit(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_JOIN_PART_QUIT] = show
        }
    }

    val showNickChangesFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading SHOW_NICK_CHANGES preferences.", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SHOW_NICK_CHANGES] ?: true
        }

    suspend fun updateShowNickChanges(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_NICK_CHANGES] = show
        }
    }

    val showModeChangesFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading SHOW_MODE_CHANGES preferences.", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SHOW_MODE_CHANGES] ?: true
        }

    suspend fun updateShowModeChanges(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_MODE_CHANGES] = show
        }
    }

    val showPingPongMessagesFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading SHOW_PING_PONG_MESSAGES preferences.", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SHOW_PING_PONG_MESSAGES] ?: false
        }

    suspend fun updateShowPingPongMessages(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_PING_PONG_MESSAGES] = show
        }
    }

    suspend fun updateNickServPassword(password: String) {
        dataStore.edit { preferences ->
            preferences[NICKSERV_PASSWORD] = password
        }
    }

    suspend fun clearNickServPassword() {
        dataStore.edit { preferences ->
            preferences.remove(NICKSERV_PASSWORD)
        }
    }
}