package com.jfaf.irc.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jfaf.irc.domain.config.AppConfig
import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.platform.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authSession: AuthSession,
    appConfig: AppConfig
) : ViewModel() {

    /** Client ID de OAuth para pedir el idToken de Google. */
    val googleSignInClientId: String = appConfig.googleSignInClientId

    val currentUser: StateFlow<AuthUser?> = authSession.currentUser

    val isUserAuthenticated: StateFlow<Boolean> = currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, currentUser.value != null)

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch { authSession.signInWithGoogleIdToken(idToken) }
    }

    fun signOut() {
        authSession.signOut()
    }
}
