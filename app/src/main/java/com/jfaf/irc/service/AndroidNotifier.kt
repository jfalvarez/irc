package com.jfaf.irc.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.jfaf.irc.MainActivity
import com.jfaf.irc.R
import com.jfaf.irc.domain.notifications.Notifier
import com.jfaf.irc.domain.notifications.PmNotificationPolicy
import com.jfaf.irc.log.Log

/**
 * [Notifier] de Android: notificaciones del sistema en el canal de mensajes. Los privados se ven como una
 * conversación (`MessagingStyle`, con los últimos mensajes) y se pueden contestar desde la notificación
 * (botón «Responder», que recibe [NotificationReplyReceiver]).
 */
class AndroidNotifier(
    private val context: Context
) : Notifier {

    companion object {
        private const val TAG = "AndroidNotifier"
        const val MESSAGE_CHANNEL_ID = "IrcMessageChannel"
        /** Extra del PendingIntent: pestaña que abre MainActivity al tocar la notificación. */
        const val EXTRA_TARGET_FOR_NOTIFICATION = "target_for_notification"
        private const val FRIEND_ONLINE_NOTIFICATION_ID = 3
        /** Mensajes de cada conversación que se ven en la notificación. */
        private const val HISTORY_SIZE = 6
    }

    /** Una línea de la conversación en la notificación; [sender] null = el propio usuario. */
    private data class Line(val sender: String?, val text: String, val time: Long, val failed: Boolean = false)

    /** Últimos mensajes de cada conversación con notificación abierta (clave: [PmNotificationPolicy.conversationKey]). */
    private val history = mutableMapOf<String, ArrayDeque<Line>>()

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createMessageChannel() // antes lo creaba IrcService.onCreate
    }

    override fun showPrivateMessage(sender: String, text: String, conversationKey: String) {
        // Si su notificación ya no está (se tocó, se descartó o se borraron todas), la conversación empieza de cero
        if (!isShowing(PmNotificationPolicy.notificationId(conversationKey))) forgetConversation(conversationKey)
        addLine(conversationKey, Line(sender, text, System.currentTimeMillis()))
        // Un id (y un requestCode) por conversación: un PM de otra persona no pisa el anterior
        // y cada notificación abre su propia conversación.
        notifyConversation(sender, conversationKey, alert = true)
        Log.d(TAG, "Notificación de privado de '$sender' (clave $conversationKey)")
    }

    /** Mensaje de un canal (UI-32): una notificación por canal, con los últimos mensajes, que al tocarla abre el canal. */
    override fun showChannelMessage(channel: String, sender: String, text: String, isMention: Boolean) {
        val key = channel.lowercase() // empieza por «#»: no choca con la clave de ningún privado
        if (!isShowing(PmNotificationPolicy.notificationId(key))) forgetConversation(key)
        addLine(key, Line(sender, text, System.currentTimeMillis()))
        notifyConversation(channel, key, alert = true)
        Log.d(TAG, "Notificación de canal '$channel' (mención: $isMention)")
    }

    /** La respuesta directa se envió: se añade a la conversación (sin volver a sonar). */
    fun onReplySent(target: String, conversationKey: String, text: String) {
        addLine(conversationKey, Line(null, text, System.currentTimeMillis()))
        notifyConversation(target, conversationKey, alert = false)
    }

    /** La respuesta no se pudo enviar (sin conexión): se muestra marcada para que se sepa. */
    fun onReplyFailed(target: String, conversationKey: String, text: String) {
        addLine(conversationKey, Line(null, text, System.currentTimeMillis(), failed = true))
        notifyConversation(target, conversationKey, alert = false)
    }

    private fun isShowing(id: Int): Boolean = notificationManager.activeNotifications.any { it.id == id }

    private fun forgetConversation(conversationKey: String) {
        synchronized(history) { history.remove(conversationKey) }
    }

    private fun addLine(conversationKey: String, line: Line) = synchronized(history) {
        val lines = history.getOrPut(conversationKey) { ArrayDeque() }
        lines.addLast(line)
        while (lines.size > HISTORY_SIZE) lines.removeFirst()
    }

    private fun notifyConversation(target: String, conversationKey: String, alert: Boolean) {
        val id = PmNotificationPolicy.notificationId(conversationKey)
        val lines = synchronized(history) { history[conversationKey]?.toList().orEmpty() }
        val me = Person.Builder().setName(context.getString(R.string.notification_me)).build()
        val them = Person.Builder().setName(target).setKey(conversationKey).build()
        // Un canal es una conversación de grupo: cada mensaje lleva a su autor y no hay botón de responder
        val group = target.startsWith("#")
        val style = NotificationCompat.MessagingStyle(me)
        if (group) style.setConversationTitle(target).setGroupConversation(true)
        for (line in lines) {
            val text = if (line.failed) context.getString(R.string.notification_reply_failed, line.text) else line.text
            val author = line.sender
            style.addMessage(text, line.time, when {
                author == null -> null
                group -> Person.Builder().setName(author).build()
                else -> them
            })
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_TARGET_FOR_NOTIFICATION, target)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val builder = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(if (group) target else context.getString(R.string.new_private_message_from_sender_title, target))
            .setContentText(lines.lastOrNull()?.text.orEmpty())
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(!alert)
            .setContentIntent(activityIntent(id, openIntent))
            .setAutoCancel(true)
        if (!group) builder.addAction(replyAction(id, target, conversationKey))
        notificationManager.notify(id, builder.build())
    }

    private fun replyAction(id: Int, target: String, conversationKey: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(NotificationReplyReceiver.KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.notification_reply_label))
            .build()
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            context.getString(R.string.notification_reply_action),
            NotificationReplyReceiver.replyIntent(context, id, target, conversationKey)
        )
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
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
            .setContentIntent(activityIntent(FRIEND_ONLINE_NOTIFICATION_ID, intent))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(FRIEND_ONLINE_NOTIFICATION_ID, notification)
        Log.d(TAG, "Notificación de amigo online mostrada para '$nick'")
    }

    private fun activityIntent(requestCode: Int, intent: Intent): PendingIntent =
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
