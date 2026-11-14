package com.jfaf.irc.data.firebase

import com.jfaf.irc.log.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.user.RemoteLists
import com.jfaf.irc.domain.user.RemoteUserLists
import com.jfaf.irc.domain.user.UserLists
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// Data class que representa la estructura de datos en Firestore (users/{uid})
data class UserMetadata(
    val ignoredNicks: List<String> = emptyList(),
    val friends: List<String> = emptyList()
)

/** Listas de la cuenta en Firestore, en el documento `users/{uid}` de siempre. */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreUserLists(
    private val firestore: FirebaseFirestore,
    authSession: AuthSession
) : RemoteUserLists {
    private val TAG = "FirestoreUserLists"

    private fun document(uid: String) = firestore.collection("users").document(uid)

    override val snapshots: Flow<RemoteLists?> = authSession.currentUser
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(null)
            } else {
                document(uid).snapshots()
                    .map<_, RemoteLists?> { snapshot ->
                        val data = snapshot.toObject<UserMetadata>() ?: UserMetadata()
                        RemoteLists(uid, UserLists(data.ignoredNicks.toSet(), data.friends.toSet()))
                    }
                    .catch { e ->
                        // Sin listas remotas se sigue en local; lo cambiado queda pendiente
                        Log.e(TAG, "Error al escuchar las listas en Firestore.", e)
                        emit(null)
                    }
            }
        }

    override fun apply(uid: String, add: UserLists, remove: UserLists) {
        // Firestore no admite arrayUnion y arrayRemove en el mismo campo de una escritura: van por separado
        write(uid, add) { FieldValue.arrayUnion(*it.toTypedArray()) }
        write(uid, remove) { FieldValue.arrayRemove(*it.toTypedArray()) }
    }

    private fun write(uid: String, lists: UserLists, op: (Set<String>) -> FieldValue) {
        val data = buildMap {
            if (lists.ignored.isNotEmpty()) put("ignoredNicks", op(lists.ignored))
            if (lists.friends.isNotEmpty()) put("friends", op(lists.friends))
        }
        if (data.isEmpty()) return
        // Sin await: Firestore aplica el cambio en su caché al momento y lo envía cuando haya red
        document(uid).set(data, SetOptions.merge())
            .addOnFailureListener { e -> Log.e(TAG, "Error al guardar las listas en Firestore.", e) }
    }
}
