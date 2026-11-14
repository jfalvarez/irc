package com.jfaf.irc.data.user

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jfaf.irc.domain.user.LocalListsState
import com.jfaf.irc.domain.user.UserList
import com.jfaf.irc.domain.user.UserLists
import com.jfaf.irc.domain.user.UserMetadataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Ignorados y amigos en el DataStore del dispositivo, sin cuenta. Nicks en minúsculas.
 * En escritorio es el almacén de la app; en Android, la parte local de [SyncedUserMetadataStore].
 * Las claves de las listas son las que ya usaba el escritorio, así que lo guardado se conserva.
 */
class LocalUserMetadataStore(private val dataStore: DataStore<Preferences>) : UserMetadataStore {

    val stateFlow: Flow<LocalListsState> = dataStore.data.map { it.toState() }

    override val ignoredUsersFlow: Flow<Set<String>> = stateFlow.map { it.lists.ignored }.distinctUntilChanged()
    override val friendsFlow: Flow<Set<String>> = stateFlow.map { it.lists.friends }.distinctUntilChanged()

    // Sin sesión de por medio: si alguna vez se sincronizó con una cuenta, queda como pendiente.
    override suspend fun addIgnoredUser(nick: String) { update { it.add(UserList.IGNORED, nick, offline = true) } }
    override suspend fun removeIgnoredUser(nick: String) { update { it.remove(UserList.IGNORED, nick, offline = true) } }
    override suspend fun addFriend(nick: String) { update { it.add(UserList.FRIENDS, nick, offline = true) } }
    override suspend fun removeFriend(nick: String) { update { it.remove(UserList.FRIENDS, nick, offline = true) } }

    /** Cambia el estado de forma atómica y devuelve el nuevo. */
    suspend fun update(transform: (LocalListsState) -> LocalListsState): LocalListsState {
        var result = LocalListsState()
        dataStore.edit { prefs ->
            result = transform(prefs.toState())
            prefs.write(result)
        }
        return result
    }

    private fun Preferences.toState() = LocalListsState(
        lists = UserLists(this[IGNORED].orEmpty(), this[FRIENDS].orEmpty()),
        ownerUid = this[OWNER],
        pendingAdd = UserLists(this[PENDING_ADD_IGNORED].orEmpty(), this[PENDING_ADD_FRIENDS].orEmpty()),
        pendingRemove = UserLists(this[PENDING_REMOVE_IGNORED].orEmpty(), this[PENDING_REMOVE_FRIENDS].orEmpty())
    )

    private fun MutablePreferences.write(state: LocalListsState) {
        this[IGNORED] = state.lists.ignored
        this[FRIENDS] = state.lists.friends
        if (state.ownerUid != null) this[OWNER] = state.ownerUid else remove(OWNER)
        this[PENDING_ADD_IGNORED] = state.pendingAdd.ignored
        this[PENDING_ADD_FRIENDS] = state.pendingAdd.friends
        this[PENDING_REMOVE_IGNORED] = state.pendingRemove.ignored
        this[PENDING_REMOVE_FRIENDS] = state.pendingRemove.friends
    }

    private companion object {
        val IGNORED = stringSetPreferencesKey("local_ignored_users")
        val FRIENDS = stringSetPreferencesKey("local_friends")
        val OWNER = stringPreferencesKey("lists_owner_uid")
        val PENDING_ADD_IGNORED = stringSetPreferencesKey("lists_pending_add_ignored")
        val PENDING_ADD_FRIENDS = stringSetPreferencesKey("lists_pending_add_friends")
        val PENDING_REMOVE_IGNORED = stringSetPreferencesKey("lists_pending_remove_ignored")
        val PENDING_REMOVE_FRIENDS = stringSetPreferencesKey("lists_pending_remove_friends")
    }
}
