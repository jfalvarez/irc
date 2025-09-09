package com.jfaf.irc.data.repositories

import android.content.Context
import android.content.Intent
import android.util.Log
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.service.IrcService
import com.jfaf.irc.service.IrcServiceApi
import dagger.hilt.android.qualifiers.ApplicationContext
// No se necesitan CoroutineScope, Dispatchers, SupervisorJob aquí directamente
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

    @Deprecated("Use disconnectAndStopService for a full cleanup including service stop", ReplaceWith("disconnectAndStopService()"))
    fun disconnect() {
        Log.d(TAG, "Solicitando SOLO desconexión del socket IRC al servicio (sin detener el servicio)")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    fun disconnectAndStopService() {
        Log.d(TAG, "Solicitando desconexión Y DETENCIÓN del servicio IRC")
        val intent = Intent(context, IrcService::class.java).apply {
            // Usaremos una nueva acción para que el servicio sepa que también debe detenerse.
            action = IrcService.ACTION_DISCONNECT_AND_STOP_SERVICE 
        }
        context.startService(intent)
    }

    fun joinChannel(channelName: String) {
        Log.d(TAG, "Solicitando unirse al canal $channelName vía servicio")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_JOIN_CHANNEL
            putExtra(IrcService.EXTRA_CHANNEL_NAME, channelName)
        }
        context.startService(intent)
    }

    fun partChannel(channelName: String) {
        Log.d(TAG, "Solicitando salir del canal $channelName vía servicio")
        val intent = Intent(context, IrcService::class.java).apply {
            action = IrcService.ACTION_PART_CHANNEL
            putExtra(IrcService.EXTRA_CHANNEL_NAME, channelName)
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
}
