package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.jfaf.irc.data.repositories.UserMetadataRepository // Importar el nuevo repositorio
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import javax.inject.Inject

class IgnoreUserUseCase @Inject constructor(
    private val strings: StringProvider,
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
                strings.get(Msg.IGNORE_REQUIRES_LOGIN),
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
            strings.get(Msg.USER_IGNORED_SYSTEM, userName)
        )

        firebaseAnalytics.logEvent("ignore_user", null)
    }
}
