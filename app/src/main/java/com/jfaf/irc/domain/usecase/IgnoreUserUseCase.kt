package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.domain.platform.AnalyticsTracker
import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.user.UserMetadataStore
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import javax.inject.Inject

class IgnoreUserUseCase @Inject constructor(
    private val strings: StringProvider,
    private val userMetadataStore: UserMetadataStore,
    private val chatStateManager: ChatStateManager,
    private val analytics: AnalyticsTracker,
    private val authSession: AuthSession
) {
    suspend operator fun invoke(userName: String) {
        // Solo proceder si hay sesión iniciada (los ignorados se guardan en la cuenta)
        if (authSession.currentUser.value == null) {
            val targetForMessage = chatStateManager.activeTarget.value
                ?: ChatStateManager.SERVER_TARGET_ID
            chatStateManager.addSystemMessageToTarget(
                targetForMessage,
                strings.get(Msg.IGNORE_REQUIRES_LOGIN),
                isError = true
            )
            return
        }

        userMetadataStore.addIgnoredUser(userName)

        // El resto de la lógica permanece igual
        val targetForMessage = chatStateManager.activeTarget.value
            ?: ChatStateManager.SERVER_TARGET_ID

        chatStateManager.addSystemMessageToTarget(
            targetForMessage,
            strings.get(Msg.USER_IGNORED_SYSTEM, userName)
        )

        analytics.logEvent("ignore_user")
    }
}
