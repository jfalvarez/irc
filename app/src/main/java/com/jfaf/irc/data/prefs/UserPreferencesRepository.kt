package com.jfaf.irc.data.prefs

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        private const val SHOW_JOIN_PART_QUIT_KEY_NAME = "show_join_part_quit"
        private const val SHOW_NICK_CHANGES_KEY_NAME = "show_nick_changes"
        private const val SHOW_MODE_CHANGES_KEY_NAME = "show_mode_changes"
        private const val SHOW_PING_PONG_KEY_NAME = "show_ping_pong_messages"
        private const val NICKSERV_PASSWORD_KEY_NAME = "nickserv_password"
        private const val SHOW_MEDIA_PREVIEWS_KEY_NAME = "show_media_previews"

        val SHOW_JOIN_PART_QUIT = booleanPreferencesKey(SHOW_JOIN_PART_QUIT_KEY_NAME)
        val SHOW_NICK_CHANGES = booleanPreferencesKey(SHOW_NICK_CHANGES_KEY_NAME)
        val SHOW_MODE_CHANGES = booleanPreferencesKey(SHOW_MODE_CHANGES_KEY_NAME)
        val SHOW_PING_PONG_MESSAGES = booleanPreferencesKey(SHOW_PING_PONG_KEY_NAME)
        val NICKSERV_PASSWORD = stringPreferencesKey(NICKSERV_PASSWORD_KEY_NAME)
        val SHOW_MEDIA_PREVIEWS = booleanPreferencesKey(SHOW_MEDIA_PREVIEWS_KEY_NAME)

        const val TAG = "UserPrefsRepository"
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

    val nickServPasswordFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading NICKSERV_PASSWORD preferences.", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[NICKSERV_PASSWORD] ?: ""
        }

    suspend fun updateNickServPassword(password: String) {
        dataStore.edit { preferences ->
            preferences[NICKSERV_PASSWORD] = password
        }
    }
}
