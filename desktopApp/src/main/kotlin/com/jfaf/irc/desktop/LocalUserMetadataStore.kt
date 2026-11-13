package com.jfaf.irc.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jfaf.irc.domain.user.UserMetadataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Ignorados y amigos guardados en el DataStore local (sin cuenta, decisión 0.1). Nicks en minúsculas. */
class LocalUserMetadataStore(private val dataStore: DataStore<Preferences>) : UserMetadataStore {

    override val ignoredUsersFlow: Flow<Set<String>> = dataStore.data.map { it[IGNORED] ?: emptySet() }
    override val friendsFlow: Flow<Set<String>> = dataStore.data.map { it[FRIENDS] ?: emptySet() }

    override suspend fun addIgnoredUser(nick: String) = add(IGNORED, nick)
    override suspend fun removeIgnoredUser(nick: String) = remove(IGNORED, nick)
    override suspend fun addFriend(nick: String) = add(FRIENDS, nick)
    override suspend fun removeFriend(nick: String) = remove(FRIENDS, nick)

    private suspend fun add(key: Preferences.Key<Set<String>>, nick: String) {
        val value = nick.lowercase()
        dataStore.edit { it[key] = (it[key] ?: emptySet()) + value }
    }

    private suspend fun remove(key: Preferences.Key<Set<String>>, nick: String) {
        val value = nick.lowercase()
        dataStore.edit { it[key] = (it[key] ?: emptySet()) - value }
    }

    private companion object {
        val IGNORED = stringSetPreferencesKey("local_ignored_users")
        val FRIENDS = stringSetPreferencesKey("local_friends")
    }
}
