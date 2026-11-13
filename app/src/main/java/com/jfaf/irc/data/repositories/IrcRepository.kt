package com.jfaf.irc.data.repositories

import android.content.Context
import android.content.Intent
import com.jfaf.irc.log.Log
import androidx.core.content.ContextCompat
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.domain.IrcConnection
import com.jfaf.irc.service.IrcService
import com.jfaf.irc.service.IrcServiceApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IrcRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ircServiceApi: IrcServiceApi
) : IrcConnection {
    private val TAG = "IrcRepository"

    private val silentWhoisNicks = ConcurrentHashMap.newKeySet<String>()

    override val connectionState: StateFlow<Boolean> = ircServiceApi.connectionState
    override val isReconnecting: StateFlow<Boolean> = ircServiceApi.isReconnecting
    override val incomingMessages: SharedFlow<ParsedIrcMessage> = ircServiceApi.incomingMessages

    override fun connect(serverHost: String, serverPort: Int, useSsl: Boolean, nickname: String) {
        Log.d(TAG, "Solicitando conexión al servicio para: $nickname@$serverHost:$serverPort")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_CONNECT
            putExtra(IrcService.EXTRA_SERVER_HOST, serverHost)
            putExtra(IrcService.EXTRA_SERVER_PORT, serverPort)
            putExtra(IrcService.EXTRA_USE_SSL, useSsl)
            putExtra(IrcService.EXTRA_NICKNAME, nickname)
        }
        sendToService(intent)
    }

    override fun disconnect(quitMessage: String?) {
        Log.d(TAG, "Solicitando desconexión Y DETENCIÓN del servicio IRC. Mensaje: $quitMessage")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_DISCONNECT_AND_STOP_SERVICE
            quitMessage?.let { putExtra(IrcService.EXTRA_QUIT_MESSAGE, it) } // Nuevo Extra
        }
        sendToService(intent)
    }

    override fun joinChannel(channelName: String, key: String?) {
        Log.d(TAG, "Solicitando unirse al canal $channelName${key?.let { " con clave" } ?: ""} vía servicio")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_JOIN_CHANNEL
            putExtra(IrcService.EXTRA_CHANNEL_NAME, channelName)
            key?.let { putExtra(IrcService.EXTRA_CHANNEL_KEY, it) } // Nuevo Extra
        }
        sendToService(intent)
    }

    override fun partChannel(channelName: String, partMessage: String?) {
        Log.d(TAG, "Solicitando salir del canal $channelName${partMessage?.let { " con mensaje: $it" } ?: ""} vía servicio")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_PART_CHANNEL
            putExtra(IrcService.EXTRA_CHANNEL_NAME, channelName)
            partMessage?.let { putExtra(IrcService.EXTRA_PART_MESSAGE, it) } // Nuevo Extra
        }
        sendToService(intent)
    }

    override fun sendMessage(target: String, message: String) {
        Log.d(TAG, "Solicitando enviar mensaje a $target: '$message' vía servicio")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_SEND_MESSAGE
            putExtra(IrcService.EXTRA_TARGET, target)
            putExtra(IrcService.EXTRA_MESSAGE, message)
        }
        sendToService(intent)
    }

    override fun sendRawCommand(command: String, isSilent: Boolean) {
        if (isSilent && command.startsWith("WHOIS", ignoreCase = true)) {
            val nick = command.split(" ").getOrNull(1)
            if (nick != null) {
                silentWhoisNicks.add(nick.lowercase())
            }
        }
        Log.d(TAG, "Solicitando enviar comando crudo: '$command' vía servicio")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_SEND_RAW_COMMAND
            putExtra(IrcService.EXTRA_RAW_COMMAND, command)
        }
        sendToService(intent)
    }

    /**
     * Arranca el servicio como foreground (válido también con la app en segundo plano desde Android 8).
     * `IrcService.onStartCommand` llama siempre a `startForeground` en los 5 s siguientes.
     */
    private fun sendToService(intent: Intent) {
        ContextCompat.startForegroundService(context, intent)
    }

    override fun isWhoisSilent(nick: String): Boolean {
        return silentWhoisNicks.contains(nick.lowercase())
    }

    override fun completeSilentWhois(nick: String) {
        silentWhoisNicks.remove(nick.lowercase())
    }
}
