package com.jfaf.irc.ui.platform

import com.jfaf.irc.domain.platform.GoogleIdTokenRequester
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pide la cuenta de Google con la pantalla de Google Sign-In para confirmar el borrado de la cuenta.
 * La pantalla sólo la puede abrir una actividad: `MainActivity` se conecta con [attach] al crearse,
 * se suelta con [detach] al destruirse y entrega lo que devuelve Google con [onResult].
 * Todo ocurre en el hilo principal.
 */
class AndroidGoogleIdTokenRequester : GoogleIdTokenRequester {
    private var launcher: (() -> Unit)? = null
    private var pending: CompletableDeferred<String?>? = null

    fun attach(launch: () -> Unit) {
        launcher = launch
    }

    /** Sólo suelta [launch] si sigue siendo el conectado (al recrear la actividad, la nueva puede llegar antes). */
    fun detach(launch: () -> Unit) {
        if (launcher !== launch) return
        launcher = null
        onResult(null) // la pantalla de Google ya no podrá contestar a esta actividad
    }

    fun onResult(idToken: String?) {
        pending?.complete(idToken)
        pending = null
    }

    override suspend fun requestIdToken(): String? {
        val result = withContext(Dispatchers.Main) {
            val launch = launcher ?: return@withContext null
            onResult(null) // una petición anterior sin respuesta queda cancelada
            CompletableDeferred<String?>().also {
                pending = it
                launch()
            }
        }
        return result?.await()
    }
}
