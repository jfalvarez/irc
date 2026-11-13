package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.log.Log
import com.jfaf.irc.domain.platform.AnalyticsTracker
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import javax.inject.Inject

sealed interface WhoisRequestResult {
    object Success : WhoisRequestResult
    object NotConnected : WhoisRequestResult
    object InvalidNickInput : WhoisRequestResult
}

class PerformWhoisUseCase @Inject constructor(
    private val strings: StringProvider,
    private val ircRepository: IrcRepository,
    private val chatStateManager: ChatStateManager,
    private val analytics: AnalyticsTracker
) {
    suspend operator fun invoke(nick: String): WhoisRequestResult {
        if (nick.isBlank()) {
            Log.w("PerformWhoisUseCase", "WHOIS attempt with blank nick.")
            // Though ViewModel might check this first, good to have self-contained validation.
            return WhoisRequestResult.InvalidNickInput 
        }

        analytics.logEvent("whois_request", mapOf("whois_target_nick" to nick))

        if (!ircRepository.connectionState.value) {
            Log.w("PerformWhoisUseCase", "performWhois called but not connected to the server.")
            chatStateManager.addSystemMessageToTarget(
                ChatStateManager.SERVER_TARGET_ID, 
                strings.get(Msg.WHOIS_NOT_CONNECTED), 
                isError = true
            )
            return WhoisRequestResult.NotConnected
        }

        Log.i("PerformWhoisUseCase", "Enviando comando WHOIS para $nick")
        chatStateManager.addSystemMessageToTarget(
            ChatStateManager.SERVER_TARGET_ID, 
            strings.get(Msg.WHOIS_REQUESTING, nick)
        )
        ircRepository.sendRawCommand("WHOIS $nick", isSilent = false)
        return WhoisRequestResult.Success
    }
}
