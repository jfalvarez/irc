package com.jfaf.irc.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.jfaf.irc.data.repositories.SignInRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signInRepository: SignInRepository
) : ViewModel() {

    private val _isUserAuthenticated = MutableStateFlow(signInRepository.currentUser != null)
    val isUserAuthenticated = _isUserAuthenticated.asStateFlow()

    fun signInWithCredential(credential: AuthCredential) {
        viewModelScope.launch {
            val user = signInRepository.signInWithCredential(credential)
            _isUserAuthenticated.value = user != null
        }
    }

    fun signOut() {
        signInRepository.signOut()
        _isUserAuthenticated.value = false
    }
}
