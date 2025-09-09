package com.jfaf.irc

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.ContextCompat // Correct import for getSystemService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IrcApplication : Application() {

    companion object {
        const val PRIVATE_MESSAGE_CHANNEL_ID = "private_message_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal para mensajes privados
            val privateMessageChannelName = "Mensajes Privados IRC"
            val privateMessageChannelDescription = "Notificaciones para nuevos mensajes privados"
            val privateMessageChannelImportance = NotificationManager.IMPORTANCE_HIGH // Permite heads-up
            val privateMessageChannel = NotificationChannel(
                PRIVATE_MESSAGE_CHANNEL_ID,
                privateMessageChannelName,
                privateMessageChannelImportance
            ).apply {
                description = privateMessageChannelDescription
                // Aquí podrías configurar más cosas como:
                // enableLights(true)
                // lightColor = Color.RED
                // enableVibration(true)
                // vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
            }

            // Obtener el NotificationManager del sistema
            val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java) as NotificationManager
            notificationManager.createNotificationChannel(privateMessageChannel)
        }
    }
}