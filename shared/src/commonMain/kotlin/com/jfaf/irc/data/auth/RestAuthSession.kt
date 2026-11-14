package com.jfaf.irc.data.auth

import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.platform.AuthUser
import com.jfaf.irc.log.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * [AuthSession] sin SDK de Firebase (escritorio): entra con el idToken de Google por [api], guarda la sesión
 * en [store] para el siguiente arranque y renueva el idToken de Firebase cuando caduca ([idToken]).
 * Al arrancar con una sesión guardada, el usuario aparece ya conectado; si al renovar Firebase dice que
 * la sesión ya no vale, se cierra. Sin red se mantiene (se vuelve a intentar en la siguiente llamada).
 */
class RestAuthSession(
    private val api: FirebaseAuthApi,
    private val store: SessionStore,
    private val now: () -> Long
) : AuthSession {

    private val mutex = Mutex()

    @Volatile
    private var tokens: FirebaseTokens? = null

    private val _currentUser = MutableStateFlow(store.load()?.let { AuthUser(it.uid, it.email) })
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    /** Comprueba al arrancar que la sesión guardada sigue valiendo. Se llama una vez, con el scope de la app. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        if (_currentUser.value != null) idToken()
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser? = mutex.withLock {
        try {
            keep(api.signInWithGoogleIdToken(idToken))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo entrar en Firebase con la cuenta de Google.", e)
            null
        }
    }

    /**
     * idToken de Firebase vigente para llamar a sus APIs (lo renueva si le quedan menos de 5 minutos).
     * null sin sesión, sin red o si la sesión ya no vale (en ese caso, además, la cierra).
     */
    suspend fun idToken(): String? = mutex.withLock {
        val user = _currentUser.value ?: return@withLock null
        tokens?.takeIf { it.uid == user.uid && it.expiresAtMillis - now() > REFRESH_MARGIN_MS }
            ?.let { return@withLock it.idToken }

        val refreshToken = tokens?.refreshToken ?: store.load()?.refreshToken
        if (refreshToken == null) {
            signOut()
            return@withLock null
        }
        try {
            val renewed = api.refresh(refreshToken, user.email)
            if (_currentUser.value?.uid != user.uid) return@withLock null // se cerró sesión mientras tanto
            keep(renewed)
            renewed.idToken
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthException) {
            if (e.invalidCredentials) {
                Log.w(TAG, "La sesión guardada ya no vale: se cierra.", e)
                signOut()
            } else {
                Log.w(TAG, "No se pudo renovar la sesión (se reintentará).", e)
            }
            null
        }
    }

    override fun signOut() {
        tokens = null
        _currentUser.value = null
        store.clear()
    }

    private fun keep(newTokens: FirebaseTokens): AuthUser {
        tokens = newTokens
        store.save(StoredSession(newTokens.uid, newTokens.email, newTokens.refreshToken))
        return AuthUser(newTokens.uid, newTokens.email).also { _currentUser.value = it }
    }

    private companion object {
        const val TAG = "RestAuthSession"
        const val REFRESH_MARGIN_MS = 5 * 60 * 1000L
    }
}
