package com.jfaf.irc.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jfaf.irc.MainActivity // Asegúrate que esta es tu Activity principal
import com.jfaf.irc.ManualIrcClient
import com.jfaf.irc.R // Asegúrate de tener un ic_notification.xml o similar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IrcService : Service() {

    private val TAG = "IrcService"
    private val NOTIFICATION_CHANNEL_ID = "IrcServiceChannel"
    private val NOTIFICATION_ID = 1

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var manualIrcClient: ManualIrcClient? = null
    private var currentHostForNotification: String = "servidor"

    companion object {
        const val ACTION_CONNECT = "com.jfaf.irc.service.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.jfaf.irc.service.ACTION_DISCONNECT"
        const val ACTION_SEND_MESSAGE = "com.jfaf.irc.service.ACTION_SEND_MESSAGE"
        const val ACTION_JOIN_CHANNEL = "com.jfaf.irc.service.ACTION_JOIN_CHANNEL"
        const val ACTION_PART_CHANNEL = "com.jfaf.irc.service.ACTION_PART_CHANNEL"
        const val ACTION_DISCONNECT_AND_STOP_SERVICE = "com.jfaf.irc.service.ACTION_DISCONNECT_AND_STOP_SERVICE"

        const val EXTRA_NICKNAME = "nickname"
        const val EXTRA_SERVER_HOST = "server_host"
        const val EXTRA_SERVER_PORT = "server_port"
        const val EXTRA_USE_SSL = "use_ssl"
        const val EXTRA_TARGET = "target"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_CHANNEL_NAME = "channel_name"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "IrcService onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand received action: $action")

        // Determinar el texto inicial de la notificación y pasar a primer plano
        // a menos que la acción sea detener el servicio.
        if (action != ACTION_DISCONNECT_AND_STOP_SERVICE) {
            val initialNotificationText = when {
                IrcServiceApi.connectionState.value -> "Estado: Conectado a $currentHostForNotification"
                action == ACTION_CONNECT -> "Iniciando conexión IRC..."
                else -> "Estado: Desconectado"
            }
            startForeground(NOTIFICATION_ID, createNotification(initialNotificationText))
        } else {
             // Si es ACTION_DISCONNECT_AND_STOP_SERVICE, la función disconnect() se encargará de stopForeground.
        }

        when (action) {
            ACTION_CONNECT -> {
                val nickname = intent.getStringExtra(EXTRA_NICKNAME) ?: "IrcUser"
                val serverHost = intent.getStringExtra(EXTRA_SERVER_HOST) ?: "irc.libera.chat"
                val serverPort = intent.getIntExtra(EXTRA_SERVER_PORT, 6667)
                val useSsl = intent.getBooleanExtra(EXTRA_USE_SSL, false)
                currentHostForNotification = serverHost // Guardar para notificaciones
                connect(nickname, serverHost, serverPort, useSsl)
            }
            ACTION_DISCONNECT -> {
                Log.i(TAG, "Acción DISCONNECT recibida. Desconectando socket, servicio permanece en foreground.")
                manualIrcClient?.disconnectAndCleanup() // Esto debería actualizar IrcServiceApi.connectionState y la notificación via flow
                // Forzamos actualización de notificación para asegurar estado "Desconectado" si el flow no lo hizo inmediatamente
                // y nos aseguramos de que IrcServiceApi también lo sepa.
                if (IrcServiceApi.connectionState.value) { // Solo si la API aún piensa que está conectado
                    IrcServiceApi.updateConnectionState(false)
                }
                updateNotification("Estado: Desconectado") 
            }
            ACTION_DISCONNECT_AND_STOP_SERVICE -> {
                Log.i(TAG, "Acción DISCONNECT_AND_STOP_SERVICE recibida. Desconectando y deteniendo el servicio.")
                disconnect()
            }
            ACTION_SEND_MESSAGE -> {
                val target = intent.getStringExtra(EXTRA_TARGET)
                val message = intent.getStringExtra(EXTRA_MESSAGE)
                if (target != null && message != null) {
                    sendMessage(target, message)
                }
            }
            ACTION_JOIN_CHANNEL -> {
                val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)
                if (channelName != null) {
                    joinChannel(channelName)
                }
            }
            ACTION_PART_CHANNEL -> {
                val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)
                if (channelName != null) {
                    partChannel(channelName)
                }
            }
            else -> {
                 Log.w(TAG, "Acción desconocida o nula: $action")
                 // Si es una acción desconocida pero el servicio está sticky y queremos mantenerlo vivo
                 // asegurar que esté en foreground si ya estaba conectado
                 if (IrcServiceApi.connectionState.value) {
                    startForeground(NOTIFICATION_ID, createNotification("Estado: Conectado a $currentHostForNotification"))
                 } else if (manualIrcClient != null) { // Si hay cliente pero no está conectado
                    startForeground(NOTIFICATION_ID, createNotification("Estado: Desconectado"))
                 } 
                 // Si no hay acción y el servicio es reiniciado por START_STICKY, podría no tener un manualIrcClient.
                 // En ese caso, la notificación de startForeground inicial ("Desconectado") es apropiada.
            }
        }
        return START_STICKY
    }

    private fun connect(nickname: String, serverHost: String, serverPort: Int, useSsl: Boolean) {
        if (manualIrcClient != null && manualIrcClient!!.isConnected) {
            Log.w(TAG, "Ya conectado o conectando.")
            currentHostForNotification = manualIrcClient?.host ?: serverHost
            updateNotification("Estado: Conectado a $currentHostForNotification") 
            IrcServiceApi.updateConnectionState(true) 
            return
        }
        Log.i(TAG, "Conectando a $serverHost:$serverPort como $nickname (SSL: $useSsl)")
        manualIrcClient?.disconnectAndCleanup()
        currentHostForNotification = serverHost

        manualIrcClient = ManualIrcClient(
            host = serverHost,
            port = serverPort,
            useSsl = useSsl,
            coroutineScope = serviceScope,
            ioDispatcher = Dispatchers.IO
        )

        serviceScope.launch {
            manualIrcClient!!.connectionState.collectLatest { isConnected ->
                IrcServiceApi.updateConnectionState(isConnected)
                val statusText = if (isConnected) "Conectado a $currentHostForNotification" else "Desconectado"
                updateNotification("Estado: $statusText")
            }
        }

        serviceScope.launch {
            manualIrcClient!!.incomingMessages.collectLatest {
                IrcServiceApi.postMessage(it)
            }
        }
        manualIrcClient?.connect(nickname)
    }

    private fun disconnect() {
        Log.i(TAG, "Función disconnect() llamada. Limpiando cliente, quitando foreground y deteniendo servicio.")
        val wasConnected = IrcServiceApi.connectionState.value
        manualIrcClient?.disconnectAndCleanup() 
        if (wasConnected) {
            IrcServiceApi.updateConnectionState(false)
        }
        stopForeground(STOP_FOREGROUND_REMOVE) 
        stopSelf() 
        Log.i(TAG, "Servicio detenido y limpiado.")
    }

    private fun sendMessage(target: String, message: String) {
        manualIrcClient?.sendMessageToChannel(target, message)
    }

    private fun joinChannel(channelName: String) {
        manualIrcClient?.joinChannel(channelName)
    }

    private fun partChannel(channelName: String) {
        manualIrcClient?.partFromChannel(channelName)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "IRC Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Cliente IRC")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground) 
            .setContentIntent(pendingIntent)
            .setOngoing(true) 
            .build()
    }

    private fun updateNotification(contentText: String) {
        // Solo se debe llamar a notify si el servicio está en primer plano.
        // Si queremos asegurar que el servicio PASE a primer plano con esta notificación,
        // deberíamos llamar a startForeground() en su lugar.
        // Como norma general, startForeground se llama para entrar/mantenerse en FG,
        // y luego notify() se usa para actualizar una notificación existente de FG.
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText)) 
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "IrcService onDestroy")
        manualIrcClient?.disconnectAndCleanup()
        serviceJob.cancel()
        if (IrcServiceApi.connectionState.value) {
            IrcServiceApi.updateConnectionState(false)
        }
    }
}
