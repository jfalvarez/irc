package com.jfaf.irc.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.domain.terms.TermsOfUse
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Si el usuario ha aceptado las condiciones de uso actuales (`App` no deja chatear hasta entonces). */
class TermsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    /** null mientras se lee el ajuste guardado (para no enseñar el diálogo un instante a quien ya aceptó). */
    val termsAccepted: StateFlow<Boolean?> = userPreferencesRepository.termsAcceptedVersionFlow
        .map { TermsOfUse.isAccepted(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun accept() {
        viewModelScope.launch { userPreferencesRepository.acceptTerms(TermsOfUse.CURRENT_VERSION) }
    }
}
