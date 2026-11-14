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
        // Sólo si el almacén puede guardar (Firestore exige sesión; el local, en escritorio, siempre)
        if (!userMetadataStore.canSave) {
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
