package com.jfaf.irc.util

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat

object NotificationHelper {

    private const val TAG = "NotificationHelper"

    // Las notificaciones de mensajes las genera IrcService (ver PmNotificationPolicy).
    fun clearAllNotifications(context: Context) {
        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java) as NotificationManager
        notificationManager.cancelAll()
        Log.i(TAG, "Todas las notificaciones han sido canceladas.")
    }
}
