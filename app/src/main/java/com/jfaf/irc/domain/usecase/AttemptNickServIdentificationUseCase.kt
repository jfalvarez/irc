package com.jfaf.irc.domain.usecase

import android.util.Log
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AttemptNickServIdentificationUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ircRepository: IrcRepository,
    private val chatStateManager: ChatStateManager
) {

    private val nickServIdentifyDelayMs = 3000L // 3 seconds delay

    suspend operator fun invoke(currentNickname: String, sessionNickServPassword: String?): String? {
        var processedSessionPassword = sessionNickServPassword
        try {
            delay(nickServIdentifyDelayMs)
            var passwordToUse: String? = processedSessionPassword

            if (!passwordToUse.isNullOrBlank()) {
                Log.i("AttemptNickServUseCase", "Using session NickServ password for identification.")
            } else {
                val savedPassword = userPreferencesRepository.userPreferencesFlow.first().nickServPassword
                if (savedPassword.isNotBlank()) {
                    Log.i("AttemptNickServUseCase", "Using saved NickServ password for identification.")
                    passwordToUse = savedPassword
                } else {
                    Log.i("AttemptNickServUseCase", "No NickServ password provided (session or saved). Skipping identification.")
                }
            }
            
            if (!passwordToUse.isNullOrBlank() && currentNickname.isNotBlank()) {
                val command = "PRIVMSG NickServ :IDENTIFY $currentNickname $passwordToUse"
                Log.i("AttemptNickServUseCase", "Attempting NickServ identification for $currentNickname.")
                chatStateManager.addSystemMessageToTarget(
                    ChatStateManager.SERVER_TARGET_ID, 
                    "Intentando identificarse con NickServ como $currentNickname..."
                )
                ircRepository.sendRawCommand(command)
            } else if (!passwordToUse.isNullOrBlank()) {
                 Log.w("AttemptNickServUseCase", "NickServ password is set, but currentNickname is blank. Cannot identify.")
            }
        } catch (e: Exception) {
            Log.e("AttemptNickServUseCase", "Error during NickServ identification attempt.", e)
        } finally {
            // Signal that the session password (if used) has been processed by returning null
            // The ViewModel will be responsible for clearing its copy of the session password.
        }
        return null // Indicates the use case has finished with the session password passed to it.
    }
}
