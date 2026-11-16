package com.jfaf.irc.data.user

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jfaf.irc.domain.user.UserMetadataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Ignorados y amigos en el DataStore del dispositivo, sin cuenta ni red. Nicks en minúsculas.
 * Es el almacén de las dos plataformas. Las claves son las de siempre, así que lo guardado se conserva.
 */
class LocalUserMetadataStore(private val dataStore: DataStore<Preferences>) : UserMetadataStore {

    override val ignoredUsersFlow: Flow<Set<String>> =
        dataStore.data.map { it[IGNORED].orEmpty() }.distinctUntilChanged()
    override val friendsFlow: Flow<Set<String>> =
        dataStore.data.map { it[FRIENDS].orEmpty() }.distinctUntilChanged()

    override suspend fun addIgnoredUser(nick: String) = add(IGNORED, nick)
    override suspend fun removeIgnoredUser(nick: String) = remove(IGNORED, nick)
    override suspend fun addFriend(nick: String) = add(FRIENDS, nick)
    override suspend fun removeFriend(nick: String) = remove(FRIENDS, nick)

    private suspend fun add(key: Preferences.Key<Set<String>>, nick: String) {
        dataStore.edit { it[key] = it[key].orEmpty() + nick.lowercase() }
    }

    private suspend fun remove(key: Preferences.Key<Set<String>>, nick: String) {
        dataStore.edit { it[key] = it[key].orEmpty() - nick.lowercase() }
    }

    private companion object {
        val IGNORED = stringSetPreferencesKey("local_ignored_users")
        val FRIENDS = stringSetPreferencesKey("local_friends")
    }
}
