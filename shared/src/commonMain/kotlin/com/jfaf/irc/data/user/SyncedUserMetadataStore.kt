package com.jfaf.irc.data.user

import com.jfaf.irc.domain.user.LocalListsState
import com.jfaf.irc.domain.user.RemoteLists
import com.jfaf.irc.domain.user.RemoteUserLists
import com.jfaf.irc.domain.user.UserList
import com.jfaf.irc.domain.user.UserLists
import com.jfaf.irc.domain.user.UserMetadataStore
import com.jfaf.irc.domain.user.reconcile
import com.jfaf.irc.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Primero local, sincronizado con la cuenta si hay sesión. La app siempre lee y escribe en [local],
 * así que las listas funcionan sin login; con sesión, cada cambio va también a [remote] y lo que
 * llega de [remote] sustituye a lo local. Lo cambiado sin sesión se sube al volver a iniciarla
 * (reglas en [reconcile]). Al cerrar sesión las listas se quedan en el dispositivo.
 */
class SyncedUserMetadataStore(
    private val local: LocalUserMetadataStore,
    private val remote: RemoteUserLists
) : UserMetadataStore {

    private val mutex = Mutex()
    private val _syncAccount = MutableStateFlow<String?>(null)

    /** Cuenta con la que se está sincronizando; null sin sesión o hasta la primera respuesta del servidor. */
    val syncAccount: StateFlow<String?> = _syncAccount.asStateFlow()

    override val ignoredUsersFlow = local.ignoredUsersFlow
    override val friendsFlow = local.friendsFlow

    /** Empieza a escuchar a la cuenta. Se llama una vez, con el scope de la app. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        remote.snapshots.collect { snapshot -> mutex.withLock { onRemote(snapshot) } }
    }

    private suspend fun onRemote(snapshot: RemoteLists?) {
        if (snapshot == null) {
            _syncAccount.value = null
            return
        }
        if (_syncAccount.value == snapshot.uid) {
            local.update { LocalListsState(snapshot.lists, snapshot.uid) }
            return
        }
        // Primera respuesta de esta sesión: juntar con lo local
        var toAdd = UserLists()
        var toRemove = UserLists()
        local.update { state ->
            val result = reconcile(state, snapshot.uid, snapshot.lists)
            toAdd = result.toAdd
            toRemove = result.toRemove
            result.state
        }
        if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
            Log.d(TAG, "Subiendo a la cuenta lo guardado sin sesión.")
            remote.apply(snapshot.uid, toAdd, toRemove)
        }
        _syncAccount.value = snapshot.uid
    }

    override suspend fun addIgnoredUser(nick: String) = change(UserList.IGNORED, nick, adding = true)
    override suspend fun removeIgnoredUser(nick: String) = change(UserList.IGNORED, nick, adding = false)
    override suspend fun addFriend(nick: String) = change(UserList.FRIENDS, nick, adding = true)
    override suspend fun removeFriend(nick: String) = change(UserList.FRIENDS, nick, adding = false)

    private suspend fun change(list: UserList, nick: String, adding: Boolean) = mutex.withLock {
        val uid = _syncAccount.value
        if (uid != null) {
            val single = UserLists.of(list, nick)
            remote.apply(uid, add = if (adding) single else UserLists(), remove = if (adding) UserLists() else single)
        }
        local.update {
            if (adding) it.add(list, nick, offline = uid == null) else it.remove(list, nick, offline = uid == null)
        }
        Unit
    }

    private companion object {
        const val TAG = "SyncedUserMetadataStore"
    }
}
