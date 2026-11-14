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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Primero local, sincronizado con la cuenta si hay sesión. La app siempre lee y escribe en [local],
 * así que las listas funcionan sin login; con sesión, cada cambio va también a [remote] y lo que
 * llega de [remote] sustituye a lo local. Lo cambiado sin sesión se sube al volver a iniciarla
 * (reglas en [reconcile]). Al cerrar sesión las listas se quedan en el dispositivo, y también al
 * borrar la cuenta ([deleteAccountData]).
 */
class SyncedUserMetadataStore(
    private val local: LocalUserMetadataStore,
    private val remote: RemoteUserLists
) : UserMetadataStore {

    private val mutex = Mutex()
    private val _syncAccount = MutableStateFlow<String?>(null)

    /** Cuenta que se está borrando (o ya borrada): lo que llegue de ella se ignora. Sólo bajo [mutex]. */
    private var deletedAccount: String? = null

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
        // Al borrar el documento llega la cuenta vacía: no debe vaciar las listas del dispositivo
        if (snapshot.uid == deletedAccount) return
        if (_syncAccount.value == snapshot.uid) {
            local.update { LocalListsState(snapshot.lists, snapshot.uid) }
            return
        }
        // Primera respuesta de esta sesión (o tras un fallo): juntar con lo local. Sólo se escribe aquí y
        // bajo el mutex, así que el estado leído no cambia hasta el update.
        val result = reconcile(local.stateFlow.first(), snapshot.uid, snapshot.lists)
        if (!result.toAdd.isEmpty() || !result.toRemove.isEmpty()) {
            Log.d(TAG, "Subiendo a la cuenta lo guardado sin sesión.")
            if (!remote.apply(snapshot.uid, result.toAdd, result.toRemove)) {
                // Lo local y sus pendientes quedan como estaban: se reintenta con la siguiente respuesta
                Log.w(TAG, "No se pudo subir lo pendiente: se reintentará.")
                return
            }
        }
        local.update { result.state }
        _syncAccount.value = snapshot.uid
    }

    override suspend fun deleteAccountData(uid: String): Boolean {
        mutex.withLock {
            deletedAccount = uid
            _syncAccount.value = null
            // Las listas se quedan en el dispositivo, como al cerrar sesión, pero ya sin cuenta asociada
            // ni pendientes: si después se entra con otra cuenta, se juntan con ella en vez de perderse.
            local.update { it.copy(ownerUid = null, pendingAdd = UserLists(), pendingRemove = UserLists()) }
        }
        return remote.delete(uid).also { deleted ->
            if (!deleted) Log.w(TAG, "No se pudieron borrar las listas de la cuenta.")
        }
    }

    override suspend fun addIgnoredUser(nick: String) = change(UserList.IGNORED, nick, adding = true)
    override suspend fun removeIgnoredUser(nick: String) = change(UserList.IGNORED, nick, adding = false)
    override suspend fun addFriend(nick: String) = change(UserList.FRIENDS, nick, adding = true)
    override suspend fun removeFriend(nick: String) = change(UserList.FRIENDS, nick, adding = false)

    private suspend fun change(list: UserList, nick: String, adding: Boolean) = mutex.withLock {
        // Primero en local, para que se vea al momento
        val uid = _syncAccount.value
        local.update { it.change(list, nick, adding, offline = uid == null) }
        if (uid != null) {
            val single = UserLists.of(list, nick)
            val saved = remote.apply(uid, add = if (adding) single else UserLists(), remove = if (adding) UserLists() else single)
            if (!saved) {
                // Queda pendiente y, hasta la siguiente respuesta de la cuenta, se trabaja como sin sesión:
                // así esa respuesta no lo pisa y, al juntarla con lo local, se vuelve a subir.
                Log.w(TAG, "No se pudo guardar en la cuenta: queda pendiente.")
                local.update { it.change(list, nick, adding, offline = true) }
                _syncAccount.value = null
            }
        }
    }

    private fun LocalListsState.change(list: UserList, nick: String, adding: Boolean, offline: Boolean) =
        if (adding) add(list, nick, offline) else remove(list, nick, offline)

    private companion object {
        const val TAG = "SyncedUserMetadataStore"
    }
}
