package com.jfaf.irc.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.platform.AuthUser
import com.jfaf.irc.log.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthSession(
    private val firebaseAuth: FirebaseAuth
) : AuthSession {

    private val _currentUser = MutableStateFlow(firebaseAuth.currentUser?.toAuthUser())
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    init {
        // Singleton de la app: el listener vive lo mismo que el proceso.
        firebaseAuth.addAuthStateListener { auth -> _currentUser.value = auth.currentUser?.toAuthUser() }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser? = try {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential).await().user?.toAuthUser()
    } catch (e: Exception) {
        Log.w("FirebaseAuthSession", "Sign-in con Google fallido", e)
        null
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, email = email)
}
