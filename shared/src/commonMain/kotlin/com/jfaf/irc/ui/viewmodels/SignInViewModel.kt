package com.jfaf.irc.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jfaf.irc.domain.config.AppConfig
import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.platform.AuthUser
import com.jfaf.irc.domain.platform.GoogleIdTokenRequester
import com.jfaf.irc.domain.usecase.DeleteAccountResult
import com.jfaf.irc.domain.usecase.DeleteAccountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SignInViewModel(
    private val authSession: AuthSession,
    appConfig: AppConfig,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val googleIdTokenRequester: GoogleIdTokenRequester
) : ViewModel() {

    /** Client ID de OAuth para pedir el idToken de Google. */
    val googleSignInClientId: String = appConfig.googleSignInClientId

    val currentUser: StateFlow<AuthUser?> = authSession.currentUser

    val isUserAuthenticated: StateFlow<Boolean> = currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, currentUser.value != null)

    /** Si la plataforma deja borrar la cuenta desde la app (botón «Eliminar cuenta»). */
    val canDeleteAccount: Boolean = authSession.supportsAccountDeletion

    private val _deletingAccount = MutableStateFlow(false)
    /** Mientras se confirma con Google y se borra. */
    val deletingAccount: StateFlow<Boolean> = _deletingAccount.asStateFlow()

    private val _deletionResult = MutableStateFlow<DeleteAccountResult?>(null)
    /** Resultado del último intento, para avisar al usuario; se limpia con [dismissDeletionResult]. */
    val deletionResult: StateFlow<DeleteAccountResult?> = _deletionResult.asStateFlow()

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch { authSession.signInWithGoogleIdToken(idToken) }
    }

    fun signOut() {
        authSession.signOut()
    }

    /** Pide confirmar con la cuenta de Google y borra la cuenta. Si el usuario cancela, no pasa nada. */
    fun deleteAccount() {
        if (_deletingAccount.value) return
        viewModelScope.launch {
            _deletingAccount.value = true
            try {
                val idToken = googleIdTokenRequester.requestIdToken() ?: return@launch
                _deletionResult.value = deleteAccountUseCase(idToken)
            } finally {
                _deletingAccount.value = false
            }
        }
    }

    fun dismissDeletionResult() {
        _deletionResult.value = null
    }
}
