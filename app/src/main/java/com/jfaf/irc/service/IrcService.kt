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
import com.jfaf.irc.data.repositories.UserMetadataRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IrcService : Service() {

    @Inject
    lateinit var userMetadataRepository: UserMetadataRepository

    private val TAG = "IrcService"
    private val FOREGROUND_NOTIFICATION_CHANNEL_ID = "IrcServiceChannel"
    private val FOREGROUND_NOTIFICATION_ID = 1

    private val MESSAGE_NOTIFICATION_CHANNEL_ID = "IrcMessageChannel"
    private val MESSAGE_NOTIFICATION_ID_BASE = 2

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var manualIrcClient: ManualIrcClient? = null
    private var currentHostForNotification: String = AppConstants.DEFAULT_HOST_PLACEHOLDER
    private var isFriendObserverStarted = false

    companion object {
        const val ACTION_CONNECT = "com.jfaf.irc.service.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.jfaf.irc.service.ACTION_DISCONNECT"
        const val ACTION_SEND_MESSAGE = "com.jfaf.irc.service.ACTION_SEND_MESSAGE"
        const val ACTION_JOIN_CHANNEL = "com.jfaf.irc.service.ACTION_JOIN_CHANNEL"
        const val ACTION_PART_CHANNEL = "com.jfaf.irc.service.ACTION_PART_CHANNEL"
        const val ACTION_DISCONNECT_AND_STOP_SERVICE = "com.jfaf.irc.service.ACTION_DISCONNECT_AND_STOP_SERVICE"
        const val ACTION_SEND_RAW_COMMAND = "com.jfaf.irc.service.ACTION_SEND_RAW_COMMAND"

        const val EXTRA_NICKNAME = "nickname"
        const val EXTRA_SERVER_HOST = "server_host"
        const val EXTRA_SERVER_PORT = "server_port"
        const val EXTRA_USE_SSL = "use_ssl"
        const val EXTRA_TARGET = "target"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_TARGET_FOR_NOTIFICATION = "target_for_notification"
        const val EXTRA_RAW_COMMAND = "raw_command"
        // Nuevos Extras
        const val EXTRA_CHANNEL_KEY = "channel_key"
        const val EXTRA_PART_MESSAGE = "part_message"
        const val EXTRA_QUIT_MESSAGE = "quit_message"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "IrcService onCreate")
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand received action: $action")

        if (!isFriendObserverStarted) {
            observeFriendOnlineStatus()
            isFriendObserverStarted = true
        }

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
                serviceScope.launch {
                    manualIrcClient?.disconnectAndCleanup()
                }
                if (IrcServiceApi.connectionState.value) {
                    IrcServiceApi.updateConnectionState(false)
                }
                updateForegroundServiceNotification(getString(R.string.notification_status_disconnected))
            }
            ACTION_DISCONNECT_AND_STOP_SERVICE -> {
                Log.i(TAG, "Acción DISCONNECT_AND_STOP_SERVICE recibida. Desconectando y deteniendo el servicio.")
                val quitMessage = intent.getStringExtra(EXTRA_QUIT_MESSAGE)
                disconnect(quitMessage)
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
                val channelKey = intent.getStringExtra(EXTRA_CHANNEL_KEY)
                if (channelName != null) {
                    joinChannel(channelName, channelKey)
                }
            }
            ACTION_PART_CHANNEL -> {
                val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)
                val partMessage = intent.getStringExtra(EXTRA_PART_MESSAGE)
                if (channelName != null) {
                    partChannel(channelName, partMessage)
                }
            }
            ACTION_SEND_RAW_COMMAND -> { 
                val rawCommand = intent.getStringExtra(EXTRA_RAW_COMMAND)
                if (!rawCommand.isNullOrBlank()) {
                    sendRawCommand(rawCommand)
                } else {
                    Log.w(TAG, "ACTION_SEND_RAW_COMMAND recibido con comando nulo o vacío.")
                }
            }
            else -> {
                 Log.w(TAG, "Acción desconocida o nula: $action")
                 if (IrcServiceApi.connectionState.value) {
                    startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification(getString(R.string.notification_status_connected_to, currentHostForNotification)))
                 } else if (manualIrcClient != null) {
                    startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification(getString(R.string.notification_status_disconnected)))
                 }
            }
        }
        return START_STICKY
    }

    private fun observeFriendOnlineStatus() {
        serviceScope.launch {
            userMetadataRepository.friendCameOnlineEvent.collectLatest { nick ->
                if (!IrcServiceApi.isAppInForeground.value) {
                    showFriendOnlineNotification(nick)
                }
            }
        }
    }

    private fun connect(nickname: String, serverHost: String, serverPort: Int, useSsl: Boolean) {
        if (manualIrcClient != null && manualIrcClient!!.isConnected) {
            Log.w(TAG, "Ya conectado o conectando.")
            currentHostForNotification = manualIrcClient?.host ?: serverHost
            updateForegroundServiceNotification(getString(R.string.notification_status_connected_to, currentHostForNotification))
            IrcServiceApi.updateConnectionState(true)
            return
        }
        Log.i(TAG, "Conectando a $serverHost:$serverPort como $nickname (SSL: $useSsl)")
        serviceScope.launch {
            manualIrcClient?.disconnectAndCleanup()
        }
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
                    val target = parsedMessage.params.firstOrNull()
                    if (parsedMessage.command == "PRIVMSG" && target != null && !target.startsWith("#")) {
                        val senderNick = parsedMessage.senderNickname
                        if (senderNick != null) {
                            val ignoredUsers = userMetadataRepository.ignoredUsersFlow.first()
                            val ignoredUsersLowercase = ignoredUsers.map { it.lowercase() }.toSet()
                            if (senderNick.lowercase() in ignoredUsersLowercase) {
                                Log.d(TAG, "App en background. Mensaje PRIVADO de usuario ignorado ($senderNick). No se muestra notificación.")
                            } else {
                                Log.d(TAG, "App en background. Mensaje PRIVADO de $senderNick no ignorado. Mostrando notificación para: ${parsedMessage.rawLine}")
                                showNewMessageNotification(parsedMessage)
                            }
                        } else {
                            Log.d(TAG, "App en background. Mensaje PRIVADO sin senderNick claro. Mostrando notificación. Raw: ${parsedMessage.rawLine}")
                            showNewMessageNotification(parsedMessage)
                        }
                    } else {
                        Log.d(TAG, "App en background. Mensaje de CANAL o no PRIVMSG. No se muestra notificación emergente para: ${parsedMessage.rawLine}")
                    }
                } else { // App is in foreground
                    Log.d(TAG, "App en foreground, no se muestra notificación emergente para: ${parsedMessage.rawLine}")
                }
            }
        }

        serviceScope.launch {
            manualIrcClient!!.connectionError.collectLatest { 
                IrcServiceApi.postConnectionError(it)
            }
        }

        manualIrcClient?.connect(nickname)
    }

    private fun disconnect(quitMessage: String? = null) {
        Log.i(TAG, "Función disconnect(quitMessage: $quitMessage) llamada. Limpiando cliente, quitando foreground y deteniendo servicio.")
        serviceScope.launch {
            val wasConnected = IrcServiceApi.connectionState.value
            if (wasConnected && !quitMessage.isNullOrBlank()) {
                manualIrcClient?.sendRaw("QUIT :$quitMessage")
            }
            manualIrcClient?.disconnectAndCleanup()
            if (wasConnected) {
                IrcServiceApi.updateConnectionState(false)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.i(TAG, "Servicio detenido y limpiado.")
        }
    }

    private fun sendMessage(target: String, message: String) {
        manualIrcClient?.sendMessageToChannel(target, message)
    }

    private fun joinChannel(channelName: String, key: String? = null) {
        manualIrcClient?.joinChannel(channelName, key) // Asumiendo que ManualIrcClient se actualizará
    }

    private fun partChannel(channelName: String, partMessage: String? = null) {
        manualIrcClient?.partFromChannel(channelName, partMessage) // Asumiendo que ManualIrcClient se actualizará
    }

    private fun sendRawCommand(command: String) {
        Log.d(TAG, "Enviando comando crudo al cliente IRC: $command")
        manualIrcClient?.sendRaw(command)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val foregroundServiceChannel = NotificationChannel(
                FOREGROUND_NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name_irc_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description_irc_service)
            }
            val messageChannel = NotificationChannel(
                MESSAGE_NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name_irc_messages),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description_irc_messages)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(foregroundServiceChannel)
            manager.createNotificationChannel(messageChannel)
            Log.d(TAG, "Canales de notificación creados/actualizados.")
        }
    }

    private fun createForegroundServiceNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
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
            .setContentTitle(getString(R.string.notification_title_irc_client))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.app_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateForegroundServiceNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification(contentText))
    }

    private fun showNewMessageNotification(parsedMessage: ParsedIrcMessage) {
        val target = parsedMessage.params.firstOrNull() ?: "Unknown"
        val sender = parsedMessage.senderNickname ?: parsedMessage.prefix ?: "Server"
        val messageContent = parsedMessage.trailing ?: ""

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_TARGET_FOR_NOTIFICATION, if (target.startsWith("#")) target else sender)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPendingIntent = PendingIntent.getActivity(this, MESSAGE_NOTIFICATION_ID_BASE, notificationIntent, pendingIntentFlags)

        val title: String
        val text: String

        if (target.startsWith("#")) {
            title = getString(R.string.new_message_in_channel_title, target)
            text = "$sender: $messageContent"
        } else {
            title = getString(R.string.new_private_message_from_sender_title, sender)
            text = messageContent
        }

        val notificationBuilder = NotificationCompat.Builder(this, MESSAGE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(MESSAGE_NOTIFICATION_ID_BASE, notificationBuilder.build())
        Log.d(TAG, "Notificación de mensaje mostrada para target '$target' o sender '$sender'")
    }

    private fun showFriendOnlineNotification(nick: String) {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPendingIntent = PendingIntent.getActivity(this, MESSAGE_NOTIFICATION_ID_BASE + 1, notificationIntent, pendingIntentFlags)

        val title = getString(R.string.friend_online_notification_title)
        val text = getString(R.string.friend_online_notification_text, nick)

        val notificationBuilder = NotificationCompat.Builder(this, MESSAGE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(MESSAGE_NOTIFICATION_ID_BASE + 1, notificationBuilder.build())
        Log.d(TAG, "Notificación de amigo online mostrada para '$nick'")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "IrcService onDestroy")
        serviceScope.launch{
            manualIrcClient?.disconnectAndCleanup()
        }
        serviceJob.cancel()
        if (IrcServiceApi.connectionState.value) {
            IrcServiceApi.updateConnectionState(false)
        }
    }
}
