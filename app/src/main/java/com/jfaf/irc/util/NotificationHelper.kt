package com.jfaf.irc.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jfaf.irc.IrcApplication // Necesario para el Channel ID
import com.jfaf.irc.MainActivity
import com.jfaf.irc.R // Necesario para R.drawable.ic_launcher_foreground

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    const val PRIVATE_MESSAGE_NOTIFICATION_ID = 1001 // ID Fijo para la notificación de PM

    fun showPrivateMessageNotification(context: Context, title: String, content: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntentFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)

        val notificationBuilder = NotificationCompat.Builder(context, IrcApplication.PRIVATE_MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Asegúrate que este recurso existe y es accesible
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java) as NotificationManager
        
        // No es necesario chequear el permiso aquí si MainActivity ya lo hace antes de llamar.
        // Sin embargo, por seguridad de la función de utilidad, se puede mantener.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || 
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(PRIVATE_MESSAGE_NOTIFICATION_ID, notificationBuilder.build())
            Log.i(TAG, "Notificación mostrada/actualizada con ID: $PRIVATE_MESSAGE_NOTIFICATION_ID. Contenido: '$content'")
        } else {
            Log.w(TAG, "No se tiene permiso para mostrar notificaciones.")
        }
    }
}