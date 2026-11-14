package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.platform.ReauthResult
import com.jfaf.irc.domain.user.UserMetadataStore
import com.jfaf.irc.log.Log

/** Cómo terminó el intento de borrar la cuenta. */
enum class DeleteAccountResult {
    DELETED,
    NOT_SIGNED_IN,
    /** Al confirmar con Google se eligió otra cuenta: no se ha borrado nada. */
    WRONG_ACCOUNT,
    /** No se pudo confirmar con Google (sin red, error de Firebase): no se ha borrado nada. */
    REAUTH_FAILED,
    /** No se pudieron borrar las listas de la cuenta: la cuenta sigue y se puede reintentar. */
    DATA_NOT_DELETED,
    /** Las listas se borraron pero la cuenta no: se puede reintentar. */
    ACCOUNT_NOT_DELETED
}

/**
 * Borra la cuenta del usuario (lo exige Google Play a las apps con inicio de sesión). Orden:
 * 1. volver a entrar con un idToken de Google recién pedido (Firebase sólo borra con un inicio reciente);
 * 2. borrar las listas de la cuenta (`users/{uid}`), dejando las del dispositivo sin cuenta asociada;
 * 3. borrar el usuario de Firebase Auth, lo que cierra la sesión.
 * Si falla un paso no se siguen los demás; repetir es seguro (borrar lo ya borrado no falla).
 */
class DeleteAccountUseCase(
    private val authSession: AuthSession,
    private val userMetadataStore: UserMetadataStore
) {
    suspend operator fun invoke(freshGoogleIdToken: String): DeleteAccountResult {
        val user = authSession.currentUser.value ?: return DeleteAccountResult.NOT_SIGNED_IN
        when (authSession.reauthenticateWithGoogleIdToken(freshGoogleIdToken)) {
            ReauthResult.OK -> Unit
            ReauthResult.WRONG_ACCOUNT -> return DeleteAccountResult.WRONG_ACCOUNT
            ReauthResult.FAILED -> return DeleteAccountResult.REAUTH_FAILED
        }
        if (!userMetadataStore.deleteAccountData(user.uid)) return DeleteAccountResult.DATA_NOT_DELETED
        if (!authSession.deleteAccount()) return DeleteAccountResult.ACCOUNT_NOT_DELETED
        Log.i(TAG, "Cuenta borrada.")
        return DeleteAccountResult.DELETED
    }

    private companion object {
        const val TAG = "DeleteAccountUseCase"
    }
}
