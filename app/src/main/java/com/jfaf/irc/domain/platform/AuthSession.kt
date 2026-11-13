package com.jfaf.irc.domain.platform

import kotlinx.coroutines.flow.StateFlow

data class AuthUser(val uid: String, val email: String?)

/** Sesión de usuario. Android: Firebase Auth con Google; escritorio: sin login (decisión 0.1). */
interface AuthSession {
    /** Usuario actual; se actualiza al iniciar o cerrar sesión. */
    val currentUser: StateFlow<AuthUser?>

    /** Inicia sesión con el idToken de Google. Devuelve el usuario o null si falla. */
    suspend fun signInWithGoogleIdToken(idToken: String): AuthUser?

    fun signOut()
}
