package com.jfaf.irc.data.auth

/** Tokens de Firebase Auth. [idToken] da acceso a las APIs de Firebase hasta [expiresAtMillis]. */
data class FirebaseTokens(
    val uid: String,
    val email: String?,
    val idToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
)

/**
 * Error de Firebase Auth. Con [invalidCredentials] la sesión ya no vale (token revocado, cuenta
 * borrada o desactivada) y hay que volver a entrar; si no, es de red, de servidor o de configuración.
 */
class FirebaseAuthException(message: String, val invalidCredentials: Boolean, cause: Throwable? = null) :
    Exception(message, cause)

/** Lo que la app necesita de Firebase Auth cuando no hay SDK (escritorio): implementado por REST en [FirebaseRestAuth]. */
interface FirebaseAuthApi {
    /** Entra en Firebase con el idToken de Google (mismo uid que en Android con la misma cuenta). */
    suspend fun signInWithGoogleIdToken(googleIdToken: String): FirebaseTokens

    /** Pide un idToken nuevo con el refresh token (el idToken dura una hora). */
    suspend fun refresh(refreshToken: String, email: String?): FirebaseTokens
}
