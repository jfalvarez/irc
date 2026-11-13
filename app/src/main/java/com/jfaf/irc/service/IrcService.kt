package com.jfaf.irc.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.jfaf.irc.log.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jfaf.irc.AppConstants
import com.jfaf.irc.MainActivity
import com.jfaf.irc.R
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.data.remote.IrcSession
import com.jfaf.irc.data.remote.SessionStatus
import com.jfaf.irc.domain.platform.AppVisibility
import com.jfaf.irc.util.NotificationHelper
import com.jfaf.irc.domain.user.UserMetadataStore
import com.jfaf.irc.domain.user.FriendPresenceTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Mantiene viva la [IrcSession] en segundo plano (servicio en primer plano) y la refleja en
 * notificaciones. La lógica de conexión y reconexión vive en la sesión, no aquí.
 */
@AndroidEntryPoint
class IrcService : Service() {

    @Inject
    lateinit var userMetadataStore: UserMetadataStore

    @Inject
    lateinit var friendPresence: FriendPresenceTracker

    @Inject
    lateinit var appVisibility: AppVisibility

    @Inject
    lateinit var session: IrcSession

    private val TAG = "IrcService"
    private val FOREGROUND_NOTIFICATION_CHANNEL_ID = "IrcServiceChannel"
    private val FOREGROUND_NOTIFICATION_ID = 1

    private val MESSAGE_NOTIFICATION_CHANNEL_ID = "IrcMessageChannel"
    private val MESSAGE_NOTIFICATION_ID_BASE = 2

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var isFriendObserverStarted = false
    private var sessionObserversJob: Job? = null

    companion object {
        /** Mantener viva la sesión: primer plano, observarla y notificar. */
        const val ACTION_START = "com.jfaf.irc.service.ACTION_START"
        /** La sesión ya está desconectada: retirar notificaciones y pararse. */
        const val ACTION_STOP = "com.jfaf.irc.service.ACTION_STOP"

        /** Extra del `PendingIntent` de una notificación de mensaje: pestaña a abrir (lo lee MainActivity). */
        const val EXTRA_TARGET_FOR_NOTIFICATION = "target_for_notification"
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

        // Siempre, también para ACTION_STOP: el servicio llega aquí vía startForegroundService
        // y debe llamar a startForeground en 5 s aunque vaya a detenerse.
        startAsForeground(createForegroundServiceNotification(statusText(session.status.value)))
        if (sessionObserversJob == null) observeSession() // tras startForeground: nunca antes

        when (action) {
            ACTION_START -> Log.i(TAG, "Servicio en primer plano: manteniendo viva la sesión IRC.")
            ACTION_STOP -> stopHosting(quitMessage = null) // la sesión ya la desconectó IrcRepository
            else -> Log.w(TAG, "Acción desconocida o nula: $action")
        }
        return START_NOT_STICKY
    }

    /** `startForeground` con el tipo declarado en el manifiesto (obligatorio con targetSdk 34+). */
    private fun startAsForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        )
    }

    /** Refleja la sesión en la notificación persistente y en las de privados. */
    private fun observeSession() {
        sessionObserversJob = serviceScope.launch {
            launch { session.status.collect { updateForegroundServiceNotification(statusText(it)) } }
            launch {
                session.incomingMessages.collect { parsedMessage ->
                    if (!appVisibility.isInForeground.value) { // App is in background
                        val target = parsedMessage.params.firstOrNull()
                        if (parsedMessage.command == "PRIVMSG" && target != null && !target.startsWith("#")) {
                            val senderNick = parsedMessage.senderNickname
                            if (senderNick != null) {
                                val ignoredUsers = userMetadataStore.ignoredUsersFlow.first()
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
        }
    }

    private fun statusText(status: SessionStatus): String = when (status) {
        SessionStatus.Disconnected -> getString(R.string.notification_status_disconnected)
        SessionStatus.Connecting -> getString(R.string.notification_status_connecting)
        is SessionStatus.Connected -> getString(R.string.notification_status_connected_to, status.host)
        is SessionStatus.Reconnecting -> getString(R.string.notification_status_reconnecting, status.attempt, status.maxAttempts)
    }

    private fun observeFriendOnlineStatus() {
        serviceScope.launch {
            friendPresence.friendCameOnlineEvent.collectLatest { nick ->
                if (!appVisibility.isInForeground.value) {
                    showFriendOnlineNotification(nick)
                }
            }
        }
    }

    /**
     * Retira las notificaciones y para el servicio. Con [quitMessage] desconecta antes la sesión
     * (cierre desde recientes); sin él, la sesión ya está desconectada.
     */
    private fun stopHosting(quitMessage: String?) {
        Log.i(TAG, "Deteniendo el servicio (quitMessage: $quitMessage).")
        serviceScope.launch {
            // Un único QUIT, que se espera a que salga antes de cerrar
            if (quitMessage != null) session.disconnect(quitMessage)
            // Deja de observar ANTES de quitar el foreground: que el "Desconectado" no repinte una notificación huérfana
            sessionObserversJob?.cancelAndJoin()
            sessionObserversJob = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            // Después de parar el foreground para que ninguna notificación (conexión, mensajes, amigos) quede huérfana.
            NotificationHelper.clearAllNotifications(this@IrcService)
            stopSelf()
            Log.i(TAG, "Servicio detenido y limpiado.")
        }
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

    /**
     * El usuario cierra la app desde la lista de recientes: se desconecta, se detiene el servicio
     * y se retiran todas las notificaciones (también la de "conectado").
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "Tarea eliminada por el usuario: desconectando y deteniendo el servicio.")
        stopHosting(AppConstants.DEFAULT_QUIT_MESSAGE)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "IrcService onDestroy")
        // Corre en el scope de la sesión: serviceJob.cancel() no impide enviar el QUIT y cerrar
        session.disconnectInBackground()
        serviceJob.cancel()
    }
}
