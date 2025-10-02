package com.jfaf.irc.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Data class que representa la estructura de datos en Firestore
data class UserMetadata(
    val ignoredNicks: List<String> = emptyList()
)

@Singleton
class UserMetadataRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) {
    private val TAG = "UserMetadataRepository"

    // Obtiene el documento del usuario actual. Devuelve null si no hay usuario logueado.
    private fun currentUserDocument() = firebaseAuth.currentUser?.uid?.let {
        firestore.collection("users").document(it)
    }

    val ignoredUsersFlow: Flow<Set<String>> = currentUserDocument()?.snapshots()?.map {
        val metadata = it.toObject<UserMetadata>()
        metadata?.ignoredNicks?.toSet() ?: emptySet()
    } ?: kotlinx.coroutines.flow.flowOf(emptySet()) // Si no hay usuario, devuelve un flow vacío.

    suspend fun addIgnoredUser(nick: String) {
        val document = currentUserDocument() ?: return
        val nickLowercase = nick.lowercase()

        document.update("ignoredNicks", FieldValue.arrayUnion(nickLowercase))
            .addOnSuccessListener { 
                Log.d(TAG, "Usuario '$nickLowercase' añadido a ignorados en Firestore.")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error al actualizar los usuarios ignorados, se intentará crear el documento.", e)
                // Asumimos que el documento no existe, así que lo creamos.
                document.set(mapOf("ignoredNicks" to listOf(nickLowercase)))
                    .addOnSuccessListener {
                        Log.d(TAG, "Documento de usuario creado y usuario '$nickLowercase' añadido a ignorados.")
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Error al crear el documento del usuario.", e2)
                    }
            }
    }

    suspend fun removeIgnoredUser(nick: String) {
        val document = currentUserDocument() ?: return
        val nickLowercase = nick.lowercase()

        document.update("ignoredNicks", FieldValue.arrayRemove(nickLowercase))
            .addOnSuccessListener { 
                 Log.d(TAG, "Usuario '$nickLowercase' eliminado de ignorados en Firestore.")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al eliminar usuario ignorado de Firestore", e)
            }
    }
}
