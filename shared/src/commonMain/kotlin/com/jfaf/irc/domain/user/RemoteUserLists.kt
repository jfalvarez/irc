package com.jfaf.irc.domain.user

import kotlinx.coroutines.flow.Flow

/** Las listas de la cuenta [uid] tal como están en el servidor. */
data class RemoteLists(val uid: String, val lists: UserLists)

/**
 * Listas guardadas en una cuenta: Android, Firestore con su SDK; escritorio, Firestore por REST.
 * Las usa [com.jfaf.irc.data.user.SyncedUserMetadataStore].
 */
interface RemoteUserLists {
    /**
     * null sin sesión (o si el servidor da error); con sesión, las listas de la cuenta, y de nuevo
     * cuando cambian (también desde otro dispositivo): al momento con el SDK, al consultar por REST.
     */
    val snapshots: Flow<RemoteLists?>

    /**
     * Añade [add] y quita [remove] en la cuenta [uid]. false si no se pudo guardar: quien llama lo
     * deja pendiente y lo reintenta. Con el SDK siempre es true (sin red, el SDK lo deja en su cola).
     */
    suspend fun apply(uid: String, add: UserLists, remove: UserLists): Boolean

    /** Borra las listas de la cuenta [uid] (antes de borrar la cuenta). false si no se pudo. */
    suspend fun delete(uid: String): Boolean = false
}
