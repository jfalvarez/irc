package com.jfaf.irc.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import com.jfaf.irc.data.repositories.SignInRepository
import com.jfaf.irc.domain.config.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signInRepository: SignInRepository,
    appConfig: AppConfig
) : ViewModel() {

    /** Client ID de OAuth para pedir el idToken de Google. */
    val googleSignInClientId: String = appConfig.googleSignInClientId

    private val _isUserAuthenticated = MutableStateFlow(signInRepository.currentUser != null)
    val isUserAuthenticated = _isUserAuthenticated.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(signInRepository.currentUser)
    val currentUser = _currentUser.asStateFlow()

    fun signInWithCredential(credential: AuthCredential) {
        viewModelScope.launch {
            val user = signInRepository.signInWithCredential(credential)
            _isUserAuthenticated.value = user != null
            _currentUser.value = user
        }
    }

    fun signOut() {
        signInRepository.signOut()
        _isUserAuthenticated.value = false
        _currentUser.value = null
    }
}
