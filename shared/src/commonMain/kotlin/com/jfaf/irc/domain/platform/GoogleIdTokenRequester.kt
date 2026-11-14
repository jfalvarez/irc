package com.jfaf.irc.domain.platform

/**
 * Pide al usuario su cuenta de Google y devuelve un idToken recién emitido, para confirmar una
 * operación delicada (borrar la cuenta). Android: la pantalla de Google Sign-In; escritorio: el navegador.
 */
interface GoogleIdTokenRequester {
    /** null si el usuario cancela o falla. */
    suspend fun requestIdToken(): String?
}

/** Sin login de Google en la plataforma. */
object NoGoogleIdTokenRequester : GoogleIdTokenRequester {
    override suspend fun requestIdToken(): String? = null
}
