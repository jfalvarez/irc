package com.jfaf.irc.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.RemoteInput
import com.jfaf.irc.domain.IrcConnection
import com.jfaf.irc.log.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * El botón «Responder» de la notificación de un privado: envía el texto escrito con [IrcConnection.sendReply]
 * y actualiza la notificación con la respuesta (o con el aviso de que no se envió). No exportado.
 */
class NotificationReplyReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        private const val TAG = "NotificationReply"
        const val KEY_REPLY_TEXT = "reply_text"
        private const val ACTION_REPLY = "com.jfaf.irc.action.REPLY"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_CONVERSATION_KEY = "conversation_key"

        /** Mutable: Android escribe en él el texto de la respuesta (RemoteInput). Explícito, así que es seguro. */
        fun replyIntent(context: Context, requestCode: Int, target: String, conversationKey: String): PendingIntent {
            val intent = Intent(context, NotificationReplyReceiver::class.java)
                .setAction(ACTION_REPLY)
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_CONVERSATION_KEY, conversationKey)
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
        }
    }

    private val connection: IrcConnection by inject()
    private val notifier: AndroidNotifier by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val key = intent.getStringExtra(EXTRA_CONVERSATION_KEY) ?: return
        val target = intent.getStringExtra(EXTRA_TARGET) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY_TEXT)?.toString()
        if (text.isNullOrBlank()) return
        if (connection.sendReply(target, text)) {
            Log.d(TAG, "Respuesta enviada a $target desde la notificación.")
            notifier.onReplySent(target, key, text.trim())
        } else {
            Log.w(TAG, "No se pudo responder a $target: sin conexión.")
            notifier.onReplyFailed(target, key, text.trim())
        }
    }
}
