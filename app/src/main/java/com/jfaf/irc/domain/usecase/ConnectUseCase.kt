package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.log.Log
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.ui.viewmodels.ChatStateManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ConnectUseCase @Inject constructor(
    private val strings: StringProvider,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ircRepository: IrcRepository,
    private val chatStateManager: ChatStateManager
) {
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

        val server = userPreferencesRepository.serverConfigFlow.first()
        val hostToConnect = server.host
        val portToConnect = server.portFor(ssl)
        
        chatStateManager.resetStateForConnection()
        // Note: MainViewModel will still set its own currentNickname and sessionNickServPasswordForAutoIdentify properties
        // The use case is responsible for the connection *process*.
        chatStateManager.addSystemMessageToTarget(
            ChatStateManager.SERVER_TARGET_ID, 
            strings.get(Msg.CONNECTING_TO, hostToConnect, nickname)
        )
        ircRepository.connect(hostToConnect, portToConnect, ssl, nickname)
        // Pestañas de canal restauradas o abiertas antes: el cliente envía los JOIN al recibir el 001.
        chatStateManager.chatTargets.value.filter { it.startsWith("#") }.forEach { ircRepository.joinChannel(it) }
    }
}
