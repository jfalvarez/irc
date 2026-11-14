package com.jfaf.irc.domain.user

import kotlinx.coroutines.flow.Flow

/** Las listas de la cuenta [uid] tal como están en el servidor. */
data class RemoteLists(val uid: String, val lists: UserLists)

/** Listas guardadas en una cuenta (Android: Firestore). Las usa [com.jfaf.irc.data.user.SyncedUserMetadataStore]. */
interface RemoteUserLists {
    /**
     * null sin sesión (o si el servidor da error); con sesión, las listas de la cuenta,
     * y de nuevo cada vez que cambian (también desde otro dispositivo).
     */
    val snapshots: Flow<RemoteLists?>

    /**
     * Añade [add] y quita [remove] en la cuenta [uid]. No espera al servidor:
     * sin red, el cambio queda en cola y se envía al volver.
     */
    fun apply(uid: String, add: UserLists, remove: UserLists)
}
