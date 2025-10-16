package com.jfaf.irc.domain.usecase

import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import javax.inject.Inject

sealed interface WhoisRequestResult {
    object Success : WhoisRequestResult
    object NotConnected : WhoisRequestResult
    object InvalidNickInput : WhoisRequestResult
}

class PerformWhoisUseCase @Inject constructor(
    private val ircRepository: IrcRepository,
    private val chatStateManager: ChatStateManager,
    private val firebaseAnalytics: FirebaseAnalytics
) {
    suspend operator fun invoke(nick: String): WhoisRequestResult {
        if (nick.isBlank()) {
            Log.w("PerformWhoisUseCase", "WHOIS attempt with blank nick.")
            // Though ViewModel might check this first, good to have self-contained validation.
            return WhoisRequestResult.InvalidNickInput 
        }

        val bundle = Bundle()
        bundle.putString("whois_target_nick", nick) 
        firebaseAnalytics.logEvent("whois_request", bundle)

        if (!ircRepository.connectionState.value) {
            Log.w("PerformWhoisUseCase", "performWhois called but not connected to the server.")
            chatStateManager.addSystemMessageToTarget(
                ChatStateManager.SERVER_TARGET_ID, 
                "No conectado. No se puede enviar WHOIS.", 
                isError = true
            )
            return WhoisRequestResult.NotConnected
        }

        Log.i("PerformWhoisUseCase", "Enviando comando WHOIS para $nick")
        chatStateManager.addSystemMessageToTarget(
            ChatStateManager.SERVER_TARGET_ID, 
            "[WHOIS] Solicitando información para $nick..."
        )
        ircRepository.sendRawCommand("WHOIS $nick", isSilent = false)
        return WhoisRequestResult.Success
    }
}
