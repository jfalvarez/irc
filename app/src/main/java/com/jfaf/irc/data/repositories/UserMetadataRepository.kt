package com.jfaf.irc.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Data class que representa la estructura de datos en Firestore
data class UserMetadata(
    val ignoredNicks: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class UserMetadataRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) {
    private val TAG = "UserMetadataRepository"

    private fun currentUserDocument() = firebaseAuth.currentUser?.uid?.let {
        firestore.collection("users").document(it)
    }

    val ignoredUsersFlow: Flow<Set<String>> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(authStateListener)
        awaitClose { firebaseAuth.removeAuthStateListener(authStateListener) }
    }.flatMapLatest { user ->
        if (user != null) {
            firestore.collection("users").document(user.uid).snapshots().map { snapshot ->
                snapshot.toObject<UserMetadata>()?.ignoredNicks?.toSet() ?: emptySet()
            }
        } else {
            flowOf(emptySet())
        }
    }

    suspend fun addIgnoredUser(nick: String) {
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

    suspend fun removeIgnoredUser(nick: String) {
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
}
