package com.jfaf.irc.desktop

import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.log.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/**
 * Lo que hace el botón «Iniciar sesión con Google» en escritorio: pide el idToken de Google en el navegador
 * ([oauth]) y entra con él en Firebase ([authSession]). Pulsar otra vez cancela el intento anterior.
 * Si el usuario cancela en el navegador no se avisa; si falla Google o Firebase, se muestra [onError].
 */
class DesktopGoogleSignIn(
    private val oauth: GoogleLoopbackOAuth,
    private val authSession: AuthSession,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit = ::showErrorDialog
) {
    private var attempt: Job? = null

    @Synchronized
    fun start(): Job {
        attempt?.cancel()
        return scope.launch {
            val ok = try {
                val idToken = oauth.requestIdToken() ?: return@launch
                authSession.signInWithGoogleIdToken(idToken) != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Falló el inicio de sesión de Google.", e)
                false
            }
            if (!ok) onError(ERROR_MESSAGE)
        }.also { attempt = it }
    }

    private companion object {
        const val TAG = "DesktopGoogleSignIn"
        const val ERROR_MESSAGE = "No se pudo iniciar sesión con Google. Inténtalo de nuevo más tarde."
    }
}

private fun showErrorDialog(message: String) {
    SwingUtilities.invokeLater {
        JOptionPane.showMessageDialog(null, message, "IRC", JOptionPane.ERROR_MESSAGE)
    }
}
