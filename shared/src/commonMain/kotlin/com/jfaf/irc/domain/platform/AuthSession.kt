package com.jfaf.irc.domain.platform

import kotlinx.coroutines.flow.StateFlow

data class AuthUser(val uid: String, val email: String?)

/** Resultado de volver a entrar antes de una operación delicada (borrar la cuenta). */
enum class ReauthResult {
    OK,
    /** El usuario eligió otra cuenta de Google distinta de la que tiene la sesión. */
    WRONG_ACCOUNT,
    FAILED
}

/** Sesión de usuario. Android: Firebase Auth con Google; escritorio: Firebase Auth por REST o sin login. */
interface AuthSession {
    /** Usuario actual; se actualiza al iniciar o cerrar sesión. */
    val currentUser: StateFlow<AuthUser?>

    /** Inicia sesión con el idToken de Google. Devuelve el usuario o null si falla. */
    suspend fun signInWithGoogleIdToken(idToken: String): AuthUser?

    fun signOut()

    /** Si se puede borrar la cuenta desde la app ([reauthenticateWithGoogleIdToken] y [deleteAccount]). */
    val supportsAccountDeletion: Boolean get() = false

    /**
     * Vuelve a entrar con un idToken de Google recién pedido, sin cambiar de usuario: Firebase sólo
     * deja borrar una cuenta si el inicio de sesión es reciente.
     */
    suspend fun reauthenticateWithGoogleIdToken(idToken: String): ReauthResult = ReauthResult.FAILED

    /** Borra la cuenta del usuario actual (y con ella la sesión). true si se borró. */
    suspend fun deleteAccount(): Boolean = false
}
