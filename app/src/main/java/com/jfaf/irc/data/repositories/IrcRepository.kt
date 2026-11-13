package com.jfaf.irc.data.repositories

import com.jfaf.irc.AppConstants
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.data.remote.ConnectParams
import com.jfaf.irc.data.remote.IrcSession
import com.jfaf.irc.di.ApplicationScope
import com.jfaf.irc.domain.IrcConnection
import com.jfaf.irc.domain.platform.ConnectionHost
import com.jfaf.irc.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** [IrcConnection] sobre la [IrcSession] del proceso; [ConnectionHost] la mantiene viva en segundo plano. */
@Singleton
class IrcRepository @Inject constructor(
    private val session: IrcSession,
    private val connectionHost: ConnectionHost,
    @ApplicationScope private val appScope: CoroutineScope
) : IrcConnection {
    private val TAG = "IrcRepository"

    private val silentWhoisNicks = ConcurrentHashMap.newKeySet<String>()

    override val connectionState: StateFlow<Boolean> = session.connectionState
    override val isReconnecting: StateFlow<Boolean> = session.isReconnecting

    // Guarda los últimos 50 para que una UI recreada (con la conexión viva) los recupere,
    // como hacía IrcServiceApi.
    private val _incomingMessages = MutableSharedFlow<ParsedIrcMessage>(
        replay = 50, extraBufferCapacity = 50, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incomingMessages: SharedFlow<ParsedIrcMessage> = _incomingMessages.asSharedFlow()

    init {
        session.incomingMessages.onEach { _incomingMessages.tryEmit(it) }.launchIn(appScope)
    }

    override fun connect(serverHost: String, serverPort: Int, useSsl: Boolean, nickname: String) {
        Log.d(TAG, "Conectando: $nickname@$serverHost:$serverPort")
        connectionHost.start()
        session.connect(ConnectParams(nickname, serverHost, serverPort, useSsl))
    }

    override fun disconnect(quitMessage: String?) {
        Log.d(TAG, "Desconectando. Mensaje: $quitMessage")
        appScope.launch {
            session.disconnect(quitMessage?.takeIf { it.isNotBlank() } ?: AppConstants.QUIT_MSG_CLIENT_SHUTDOWN)
            connectionHost.stop()
        }
    }

    override fun joinChannel(channelName: String, key: String?) = session.joinChannel(channelName, key)
    override fun partChannel(channelName: String, partMessage: String?) = session.partChannel(channelName, partMessage)
    override fun sendMessage(target: String, message: String) = session.sendMessage(target, message)

    override fun sendRawCommand(command: String, isSilent: Boolean) {
        if (isSilent && command.startsWith("WHOIS", ignoreCase = true)) {
            command.split(" ").getOrNull(1)?.let { silentWhoisNicks.add(it.lowercase()) }
        }
        session.sendRaw(command)
    }

    override fun isWhoisSilent(nick: String): Boolean = silentWhoisNicks.contains(nick.lowercase())
    override fun completeSilentWhois(nick: String) { silentWhoisNicks.remove(nick.lowercase()) }
}
