package com.jfaf.irc.domain.usecase

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.jfaf.irc.data.repositories.UserMetadataRepository // Importar el nuevo repositorio
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import javax.inject.Inject

class IgnoreUserUseCase @Inject constructor(
    private val userMetadataRepository: UserMetadataRepository, // Reemplazar UserPreferencesRepository
    private val chatStateManager: ChatStateManager,
    private val firebaseAnalytics: FirebaseAnalytics,
    private val firebaseAuth: FirebaseAuth // Añadir FirebaseAuth
) {
    suspend operator fun invoke(userName: String) {
        // Solo proceder si el usuario está logueado en Firebase
        if (firebaseAuth.currentUser == null) {
            val targetForMessage = chatStateManager.activeTarget.value
                ?: ChatStateManager.SERVER_TARGET_ID
            chatStateManager.addSystemMessageToTarget(
                targetForMessage,
                "No puedes ignorar usuarios si no has iniciado sesión.",
                isError = true
            )
            return
        }

        userMetadataRepository.addIgnoredUser(userName)

        // El resto de la lógica permanece igual
        val targetForMessage = chatStateManager.activeTarget.value
            ?: ChatStateManager.SERVER_TARGET_ID

        chatStateManager.addSystemMessageToTarget(
            targetForMessage,
            "Usuario '$userName' ahora está en la lista de ignorados."
        )

        firebaseAnalytics.logEvent("ignore_user", null)
    }
}
