package com.jfaf.irc.data.prefs

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
        val SHOW_JOIN_PART_QUIT = booleanPreferencesKey("show_join_part_quit")
        val SHOW_NICK_CHANGES = booleanPreferencesKey("show_nick_changes") // Nueva clave
        val SHOW_MODE_CHANGES = booleanPreferencesKey("show_mode_changes") // Nueva clave
        const val TAG = "UserPrefsRepository"
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
            preferences[SHOW_JOIN_PART_QUIT] ?: true // Default to true if not set
        }

    suspend fun updateShowJoinPartQuit(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_JOIN_PART_QUIT] = show
        }
    }

    // Flow para Show Nick Changes
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
            preferences[SHOW_NICK_CHANGES] ?: true // Default to true
        }

    suspend fun updateShowNickChanges(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_NICK_CHANGES] = show
        }
    }

    // Flow para Show Mode Changes
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
            preferences[SHOW_MODE_CHANGES] ?: true // Default to true
        }

    suspend fun updateShowModeChanges(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_MODE_CHANGES] = show
        }
    }
}
