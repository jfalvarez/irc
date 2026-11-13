package com.jfaf.irc

import android.app.Application
import android.app.NotificationManager
import co.touchlab.kermit.platformLogWriter
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import com.google.android.gms.ads.MobileAds
import com.jfaf.irc.di.androidModule
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import com.jfaf.irc.log.Log
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class IrcApplication : Application(), SingletonImageLoader.Factory {

    private companion object {
        /** Canal de versiones antiguas que ya nadie usa (los avisos salen por `AndroidNotifier` e `IrcService`). */
        const val LEGACY_PRIVATE_MESSAGE_CHANNEL_ID = "private_message_channel"
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

        // Quita de los ajustes del sistema el canal de versiones anteriores (si no existe, no hace nada)
        getSystemService(NotificationManager::class.java).deleteNotificationChannel(LEGACY_PRIVATE_MESSAGE_CHANNEL_ID)
    }

    // Coil 3: el ImageLoader global añade el decodificador de GIF animado (API 28+; minSdk es 29).
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(AnimatedImageDecoder.Factory()) }
            .build()
}
