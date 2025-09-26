package com.jfaf.irc.domain.usecase

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import javax.inject.Inject

class IgnoreUserUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val chatStateManager: ChatStateManager,
    private val firebaseAnalytics: FirebaseAnalytics
) {
    suspend operator fun invoke(userName: String) {
        userPreferencesRepository.addIgnoredUser(userName)
        
        // Determine target for system message
        val targetForMessage = chatStateManager.activeTarget.value 
            ?: ChatStateManager.SERVER_TARGET_ID
            
        chatStateManager.addSystemMessageToTarget(
            targetForMessage, 
            "Usuario '$userName' ahora está en la lista de ignorados."
        )
        
        // Log Firebase Analytics event without parameters as in the original ViewModel
        firebaseAnalytics.logEvent("ignore_user", null)
    }
}
