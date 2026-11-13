package com.jfaf.irc.data.prefs

import com.jfaf.irc.domain.chat.RecentEmojis
import com.jfaf.irc.log.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import okio.IOException

data class UserPreferences(val nickname: String, val nickServPassword: String)

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
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
        val NICKNAME = stringPreferencesKey(NICKNAME_KEY_NAME)

        val SOUND_ALERTS = booleanPreferencesKey("sound_alerts")
        val RECENT_EMOJIS = stringPreferencesKey("recent_emojis")

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
            preferences[SHOW_MEDIA_PREVIEWS] ?: true
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