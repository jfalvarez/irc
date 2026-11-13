package com.jfaf.irc.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jfaf.irc.MainActivity
import com.jfaf.irc.R
import com.jfaf.irc.domain.notifications.Notifier
import com.jfaf.irc.domain.notifications.PmNotificationPolicy
import com.jfaf.irc.log.Log

/** [Notifier] de Android: notificaciones del sistema en el canal de mensajes. */
class AndroidNotifier(
    private val context: Context
) : Notifier {

    companion object {
        private const val TAG = "AndroidNotifier"
        const val MESSAGE_CHANNEL_ID = "IrcMessageChannel"
        /** Extra del PendingIntent: pestaña que abre MainActivity al tocar la notificación. */
        const val EXTRA_TARGET_FOR_NOTIFICATION = "target_for_notification"
        private const val FRIEND_ONLINE_NOTIFICATION_ID = 3
    }

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createMessageChannel() // antes lo creaba IrcService.onCreate
    }

    override fun showPrivateMessage(sender: String, text: String, conversationKey: String) {
        // Un id (y un requestCode) por conversación: un PM de otra persona no pisa el anterior
        // y cada notificación abre su propia conversación.
        val id = PmNotificationPolicy.notificationId(conversationKey)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_TARGET_FOR_NOTIFICATION, sender)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(context.getString(R.string.new_private_message_from_sender_title, sender))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent(id, intent))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id, notification)
        Log.d(TAG, "Notificación de privado de '$sender' (id $id)")
    }

    override fun showFriendOnline(nick: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(context.getString(R.string.friend_online_notification_title))
            .setContentText(context.getString(R.string.friend_online_notification_text, nick))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent(FRIEND_ONLINE_NOTIFICATION_ID, intent))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(FRIEND_ONLINE_NOTIFICATION_ID, notification)
        Log.d(TAG, "Notificación de amigo online mostrada para '$nick'")
    }

    private fun pendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun createMessageChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                context.getString(R.string.notification_channel_name_irc_messages),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description_irc_messages)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
