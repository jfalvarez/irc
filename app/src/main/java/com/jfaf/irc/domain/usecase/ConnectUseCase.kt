package com.jfaf.irc.domain.usecase

import android.util.Log
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import javax.inject.Inject

class ConnectUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ircRepository: IrcRepository,
    private val chatStateManager: ChatStateManager
) {
    // Consider making defaultHost configurable or part of UserPreferencesRepository if needed
    private val defaultHost = "irc.irc-hispano.org" 

    suspend operator fun invoke(
        nickname: String,
        ssl: Boolean,
        sessionNickServPasswordParam: String?,
        rememberPass: Boolean
    ) {
        // Handle NickServ password preference
        if (rememberPass && !sessionNickServPasswordParam.isNullOrBlank()) {
            Log.i("ConnectUseCase", "Remembering NickServ password.")
            userPreferencesRepository.updateNickServPassword(sessionNickServPasswordParam)
        } else if (!rememberPass) {
            // If not remembering, or if remembering but the param is blank, clear it.
            // This handles the case where user unchecks "remember" or clears the password field while "remember" is checked.
            Log.i("ConnectUseCase", "Clearing any saved NickServ password as 'Remember' was unchecked or password field was blank with remember checked.")
            userPreferencesRepository.updateNickServPassword("")
        }
        // If rememberPass is true but sessionNickServPasswordParam is blank, the above logic correctly clears the saved password.
        // If rememberPass is false, the saved password is also cleared.

        val hostToConnect = defaultHost
        val portToConnect = if (ssl) 6697 else 6667
        
        chatStateManager.resetStateForConnection()
        // Note: MainViewModel will still set its own currentNickname and sessionNickServPasswordForAutoIdentify properties
        // The use case is responsible for the connection *process*.
        chatStateManager.addSystemMessageToTarget(
            ChatStateManager.SERVER_TARGET_ID, 
            "Conectando a $hostToConnect como $nickname..."
        )
        ircRepository.connect(hostToConnect, portToConnect, ssl, nickname)
    }
}
