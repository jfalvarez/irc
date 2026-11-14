package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.domain.platform.AnalyticsTracker
import com.jfaf.irc.domain.user.UserMetadataStore
import com.jfaf.irc.domain.chat.ChatStateManager

class IgnoreUserUseCase(
    private val strings: StringProvider,
    private val userMetadataStore: UserMetadataStore,
    private val chatStateManager: ChatStateManager,
    private val analytics: AnalyticsTracker
) {
    suspend operator fun invoke(userName: String) {
        // Se guarda siempre en el dispositivo (y en la cuenta, si hay sesión): no hace falta login
        userMetadataStore.addIgnoredUser(userName)

        val targetForMessage = chatStateManager.activeTarget.value
            ?: ChatStateManager.SERVER_TARGET_ID

        chatStateManager.addSystemMessageToTarget(
            targetForMessage,
            strings.get(Msg.USER_IGNORED_SYSTEM, userName)
        )

        analytics.logEvent("ignore_user")
    }
}
