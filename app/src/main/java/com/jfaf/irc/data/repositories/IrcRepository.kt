package com.jfaf.irc.data.repositories

import android.content.Context
import android.content.Intent
import android.util.Log
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.service.IrcService
import com.jfaf.irc.service.IrcServiceApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IrcRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "IrcRepository"

    val connectionState: StateFlow<Boolean> = IrcServiceApi.connectionState
    val incomingMessages: SharedFlow<ParsedIrcMessage> = IrcServiceApi.incomingMessages

    fun connect(serverHost: String, serverPort: Int, useSsl: Boolean, nickname: String) {
        Log.d(TAG, "Solicitando conexión al servicio para: $nickname@$serverHost:$serverPort")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_CONNECT
            putExtra(IrcService.EXTRA_SERVER_HOST, serverHost)
            putExtra(IrcService.EXTRA_SERVER_PORT, serverPort)
            putExtra(IrcService.EXTRA_USE_SSL, useSsl)
            putExtra(IrcService.EXTRA_NICKNAME, nickname)
        }
        context.startService(intent)
    }

    @Deprecated("Use disconnectAndStopService for a full cleanup including service stop", ReplaceWith("disconnectAndStopService(null)"))
    fun disconnect() {
        Log.d(TAG, "Solicitando SOLO desconexión del socket IRC al servicio (sin detener el servicio)")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    fun disconnectAndStopService(quitMessage: String? = null) {
        Log.d(TAG, "Solicitando desconexión Y DETENCIÓN del servicio IRC. Mensaje: $quitMessage")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_DISCONNECT_AND_STOP_SERVICE
            quitMessage?.let { putExtra(IrcService.EXTRA_QUIT_MESSAGE, it) } // Nuevo Extra
        }
        context.startService(intent)
    }

    fun joinChannel(channelName: String, key: String? = null) {
        Log.d(TAG, "Solicitando unirse al canal $channelName${key?.let { " con clave" } ?: ""} vía servicio")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_JOIN_CHANNEL
            putExtra(IrcService.EXTRA_CHANNEL_NAME, channelName)
            key?.let { putExtra(IrcService.EXTRA_CHANNEL_KEY, it) } // Nuevo Extra
        }
        context.startService(intent)
    }

    fun partChannel(channelName: String, partMessage: String? = null) {
        Log.d(TAG, "Solicitando salir del canal $channelName${partMessage?.let { " con mensaje: $it" } ?: ""} vía servicio")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_PART_CHANNEL
            putExtra(IrcService.EXTRA_CHANNEL_NAME, channelName)
            partMessage?.let { putExtra(IrcService.EXTRA_PART_MESSAGE, it) } // Nuevo Extra
        }
        context.startService(intent)
    }

    fun sendMessage(target: String, message: String) {
        Log.d(TAG, "Solicitando enviar mensaje a $target: '$message' vía servicio")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_SEND_MESSAGE
            putExtra(IrcService.EXTRA_TARGET, target)
            putExtra(IrcService.EXTRA_MESSAGE, message)
        }
        context.startService(intent)
    }

    fun sendRawCommand(command: String) {
        Log.d(TAG, "Solicitando enviar comando crudo: '$command' vía servicio")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_SEND_RAW_COMMAND
            putExtra(IrcService.EXTRA_RAW_COMMAND, command)
        }
        context.startService(intent)
    }
}
