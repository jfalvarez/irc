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
import com.jfaf.irc.AppConstants
import com.jfaf.irc.MainActivity
import com.jfaf.irc.ManualIrcClient
import com.jfaf.irc.R
import com.jfaf.irc.data.model.ParsedIrcMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class IrcService : Service() {

    private val TAG = "IrcService"
    private val FOREGROUND_NOTIFICATION_CHANNEL_ID = "IrcServiceChannel" // MODIFICADO: Renombrado para claridad
    private val FOREGROUND_NOTIFICATION_ID = 1 // MODIFICADO: Renombrado para claridad

    // NUEVO: IDs para notificaciones de mensajes
    private val MESSAGE_NOTIFICATION_CHANNEL_ID = "IrcMessageChannel"
    private val MESSAGE_NOTIFICATION_ID_BASE = 2 // Usaremos esto como base, podríamos hacerlo más dinámico

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var manualIrcClient: ManualIrcClient? = null
    private var currentHostForNotification: String = AppConstants.DEFAULT_HOST_PLACEHOLDER

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

        // NUEVO: Para pasar info del mensaje a MainActivity desde la notificación
        const val EXTRA_TARGET_FOR_NOTIFICATION = "target_for_notification"

    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "IrcService onCreate")
        createNotificationChannels() // MODIFICADO: Nombre de la función pluralizado
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand received action: $action")

        if (action != ACTION_DISCONNECT_AND_STOP_SERVICE) {
            val initialNotificationText = when {
                IrcServiceApi.connectionState.value -> getString(R.string.notification_status_connected_to, currentHostForNotification)
                action == ACTION_CONNECT -> getString(R.string.notification_status_connecting)
                else -> getString(R.string.notification_status_disconnected)
            }
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification(initialNotificationText))
        }

        when (action) {
            ACTION_CONNECT -> {
                val nickname = intent.getStringExtra(EXTRA_NICKNAME) ?: AppConstants.DEFAULT_NICKNAME
                val serverHost = intent.getStringExtra(EXTRA_SERVER_HOST) ?: AppConstants.DEFAULT_SERVER_HOST
                val serverPort = intent.getIntExtra(EXTRA_SERVER_PORT, 6667)
                val useSsl = intent.getBooleanExtra(EXTRA_USE_SSL, false)
                currentHostForNotification = serverHost
                connect(nickname, serverHost, serverPort, useSsl)
            }
            ACTION_DISCONNECT -> {
                Log.i(TAG, "Acción DISCONNECT recibida. Desconectando socket, servicio permanece en foreground.")
                manualIrcClient?.disconnectAndCleanup()
                if (IrcServiceApi.connectionState.value) {
                    IrcServiceApi.updateConnectionState(false)
                }
                updateForegroundServiceNotification(getString(R.string.notification_status_disconnected))
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
                 if (IrcServiceApi.connectionState.value) {
                    startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification(getString(R.string.notification_status_connected_to, currentHostForNotification)))
                 } else if (manualIrcClient != null) { // Evitar null si el servicio se reinicia sin cliente
                    startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification(getString(R.string.notification_status_disconnected)))
                 }
            }
        }
        return START_STICKY
    }

    private fun connect(nickname: String, serverHost: String, serverPort: Int, useSsl: Boolean) {
        if (manualIrcClient != null && manualIrcClient!!.isConnected) {
            Log.w(TAG, "Ya conectado o conectando.")
            currentHostForNotification = manualIrcClient?.host ?: serverHost
            updateForegroundServiceNotification(getString(R.string.notification_status_connected_to, currentHostForNotification))
            IrcServiceApi.updateConnectionState(true) // Asegurar que el estado se refleja
            return
        }
        Log.i(TAG, "Conectando a $serverHost:$serverPort como $nickname (SSL: $useSsl)")
        manualIrcClient?.disconnectAndCleanup() // Limpia cliente anterior si existe
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
                val statusText = if (isConnected) getString(R.string.status_connected_to_host, currentHostForNotification) else getString(R.string.status_disconnected_short)
                updateForegroundServiceNotification(statusText)
            }
        }

        serviceScope.launch {
            manualIrcClient!!.incomingMessages.collectLatest { parsedMessage ->
                IrcServiceApi.postMessage(parsedMessage)

                if (!IrcServiceApi.isAppInForeground.value) { // App is in background
                    // Check if it's a private message
                    // A private message is a PRIVMSG where the target (first param) is our nick (i.e., not a channel)
                    val target = parsedMessage.params.firstOrNull()
                    if (parsedMessage.command == "PRIVMSG" && target != null && !target.startsWith("#")) {
                        Log.d(TAG, "App en background. Mensaje PRIVADO detectado. Mostrando notificación para: ${parsedMessage.rawLine}")
                        showNewMessageNotification(parsedMessage)
                    } else {
                        Log.d(TAG, "App en background. Mensaje de CANAL o no PRIVMSG. No se muestra notificación emergente para: ${parsedMessage.rawLine}")
                    }
                } else { // App is in foreground
                    Log.d(TAG, "App en foreground, no se muestra notificación emergente para: ${parsedMessage.rawLine}")
                }
            }
        }
        manualIrcClient?.connect(nickname)
    }

    private fun disconnect() {
        Log.i(TAG, "Función disconnect() llamada. Limpiando cliente, quitando foreground y deteniendo servicio.")
        val wasConnected = IrcServiceApi.connectionState.value
        manualIrcClient?.disconnectAndCleanup()
        if (wasConnected) { // Solo actualiza si realmente estaba conectado
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

    // MODIFICADO: Ahora crea ambos canales
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val foregroundServiceChannel = NotificationChannel(
                FOREGROUND_NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name_irc_service), // Debes tener este string
                NotificationManager.IMPORTANCE_LOW // Usar LOW para que no sea intrusiva
            ).apply {
                description = getString(R.string.notification_channel_description_irc_service) // Debes tener este string
            }

            // NUEVO: Canal para mensajes
            val messageChannel = NotificationChannel(
                MESSAGE_NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name_irc_messages), // Debes tener este string
                NotificationManager.IMPORTANCE_HIGH // HIGH para heads-up, sonido, vibración
            ).apply {
                description = getString(R.string.notification_channel_description_irc_messages) // Debes tener este string
                // Aquí puedes configurar luces, patrón de vibración, etc.
                // enableLights(true)
                // lightColor = Color.CYAN
                // enableVibration(true)
                // vibrationPattern = longArrayOf(0, 500, 250, 500)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(foregroundServiceChannel)
            manager.createNotificationChannel(messageChannel)
            Log.d(TAG, "Canales de notificación creados/actualizados.")
        }
    }

    // MODIFICADO: Renombrado para claridad
    private fun createForegroundServiceNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Para comportamiento estándar al abrir desde notificación
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )

        return NotificationCompat.Builder(this, FOREGROUND_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title_irc_client)) // Debes tener este string
            .setContentText(contentText)
            .setSmallIcon(R.drawable.app_icon) // Usa un icono específico para notificaciones (monocromático)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Notificación persistente del servicio
            .setSilent(true) // Para que las actualizaciones no suenen si usas IMPORTANCE_LOW o DEFAULT
            .build()
    }

    // MODIFICADO: Renombrado para claridad
    private fun updateForegroundServiceNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification(contentText))
    }

    // NUEVO: Función para mostrar notificaciones de mensajes
    private fun showNewMessageNotification(parsedMessage: ParsedIrcMessage) {
        val target = parsedMessage.params.firstOrNull() ?: "Unknown"
        val sender = parsedMessage.senderNickname ?: parsedMessage.prefix ?: "Server"
        val messageContent = parsedMessage.trailing ?: ""

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            // Pasa información para que MainActivity pueda abrir el chat correcto
            putExtra(EXTRA_TARGET_FOR_NOTIFICATION, if (target.startsWith("#")) target else sender)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // O FLAG_ACTIVITY_SINGLE_TOP si prefieres
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        // Usar un requestCode diferente para cada PendingIntent si quieres que sean únicos (ej., basado en target)
        // Por ahora, un requestCode fijo es suficiente si siempre actualiza la misma MainActivity.
        val contentPendingIntent = PendingIntent.getActivity(this, MESSAGE_NOTIFICATION_ID_BASE, notificationIntent, pendingIntentFlags)

        val title: String
        val text: String

        if (target.startsWith("#")) { // Mensaje de canal
            title = getString(R.string.new_message_in_channel_title, target) // Ej: "Nuevo mensaje en #canal"
            text = "$sender: $messageContent"
        } else { // Mensaje privado (el target es nuestro nick, el sender es el otro usuario)
            title = getString(R.string.new_private_message_from_sender_title, sender) // Ej: "Mensaje privado de UsuarioX"
            text = messageContent
        }

        val notificationBuilder = NotificationCompat.Builder(this, MESSAGE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon) // Icono específico para mensajes
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Para heads-up
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true) // La notificación desaparece al pulsarla
            // .setGroup(GROUP_KEY_MESSAGES) // Opcional: para agrupar notificaciones

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Usar un ID de notificación diferente para cada chat o uno que se actualice.
        // Por simplicidad, usamos un ID fijo por ahora. Si quieres múltiples notificaciones,
        // necesitarás un ID único por conversación (ej. target.hashCode()).
        notificationManager.notify(MESSAGE_NOTIFICATION_ID_BASE, notificationBuilder.build())
        Log.d(TAG, "Notificación de mensaje mostrada para target '$target' o sender '$sender'")
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
            IrcServiceApi.updateConnectionState(false) // Asegurar que el estado se actualiza al destruir
        }
    }
}
