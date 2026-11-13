package com.jfaf.irc

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.ContextCompat // Correct import for getSystemService
import com.google.android.gms.ads.MobileAds // Import MobileAds
import co.touchlab.kermit.platformLogWriter
import com.jfaf.irc.log.Log
import com.jfaf.irc.di.androidModule
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class IrcApplication : Application() {

    companion object {
        const val PRIVATE_MESSAGE_CHANNEL_ID = "private_message_channel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.install(platformLogWriter()) // Logcat en Android
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@IrcApplication)
            modules(commonModule, viewModelModule, androidModule)
        }
        
        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}

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