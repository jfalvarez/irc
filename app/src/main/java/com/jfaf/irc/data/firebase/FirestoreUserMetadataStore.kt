package com.jfaf.irc.data.firebase

import com.jfaf.irc.log.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.user.UserMetadataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Data class que representa la estructura de datos en Firestore
data class UserMetadata(
    val ignoredNicks: List<String> = emptyList(),
    val friends: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreUserMetadataStore @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authSession: AuthSession
) : UserMetadataStore {
    private val TAG = "FirestoreUserMetadataStore"

    private fun currentUserDocument() = authSession.currentUser.value?.uid?.let {
        firestore.collection("users").document(it)
    }

    private val uidFlow: Flow<String?> = authSession.currentUser.map { it?.uid }.distinctUntilChanged()

    override val ignoredUsersFlow: Flow<Set<String>> = uidFlow.flatMapLatest { uid ->
        if (uid != null) {
            firestore.collection("users").document(uid).snapshots().map { snapshot ->
                snapshot.toObject<UserMetadata>()?.ignoredNicks?.toSet() ?: emptySet()
            }
        } else {
            flowOf(emptySet())
        }
    }

    override val friendsFlow: Flow<Set<String>> = uidFlow.flatMapLatest { uid ->
        if (uid != null) {
            firestore.collection("users").document(uid).snapshots().map { snapshot ->
                snapshot.toObject<UserMetadata>()?.friends?.toSet() ?: emptySet()
            }
        } else {
            flowOf(emptySet())
        }
    }

    override suspend fun addIgnoredUser(nick: String) {
        val document = currentUserDocument() ?: return
        val nickLowercase = nick.lowercase()

        try {
            val data = mapOf("ignoredNicks" to FieldValue.arrayUnion(nickLowercase))
            document.set(data, SetOptions.merge()).await()
            Log.d(TAG, "Usuario '$nickLowercase' añadido/actualizado en ignorados en Firestore.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al añadir usuario ignorado a Firestore.", e)
        }
    }

    override suspend fun removeIgnoredUser(nick: String) {
        val document = currentUserDocument() ?: return
        val nickLowercase = nick.lowercase()

        try {
            document.update("ignoredNicks", FieldValue.arrayRemove(nickLowercase)).await()
            Log.d(TAG, "Usuario '$nickLowercase' eliminado de ignorados en Firestore.")
        } catch (e: Exception) {
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                Log.w(TAG, "Documento no encontrado al intentar eliminar, no se hace nada.")
            } else {
                Log.e(TAG, "Error al eliminar usuario ignorado de Firestore", e)
            }
        }
    }

    override suspend fun addFriend(nick: String) {
        val document = currentUserDocument() ?: return
        val nickLowercase = nick.lowercase()

        try {
            val data = mapOf("friends" to FieldValue.arrayUnion(nickLowercase))
            document.set(data, SetOptions.merge()).await()
            Log.d(TAG, "Amigo '$nickLowercase' añadido/actualizado en Firestore.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al añadir amigo a Firestore.", e)
        }
    }

    override suspend fun removeFriend(nick: String) {
        val document = currentUserDocument() ?: return
        val nickLowercase = nick.lowercase()

        try {
            document.update("friends", FieldValue.arrayRemove(nickLowercase)).await()
            Log.d(TAG, "Amigo '$nickLowercase' eliminado de Firestore.")
        } catch (e: Exception) {
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                Log.w(TAG, "Documento no encontrado al intentar eliminar, no se hace nada.")
            } else {
                Log.e(TAG, "Error al eliminar amigo de Firestore", e)
            }
        }
    }
}
