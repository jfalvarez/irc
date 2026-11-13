package com.jfaf.irc.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.jfaf.irc.ads.NativeAdManager
import com.jfaf.irc.config.RemoteConfigManager
import com.jfaf.irc.data.firebase.FirebaseAnalyticsTracker
import com.jfaf.irc.data.firebase.FirebaseAuthSession
import com.jfaf.irc.data.firebase.FirestoreUserMetadataStore
import com.jfaf.irc.data.history.room.CHAT_DATABASE_NAME
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.data.history.room.buildChatDatabase
import com.jfaf.irc.data.prefs.USER_PREFERENCES_FILE
import com.jfaf.irc.data.prefs.createPreferencesDataStore
import com.jfaf.irc.data.remote.IrcTransport
import com.jfaf.irc.data.remote.JvmSocketTransport
import com.jfaf.irc.data.text.ComposeStringProvider
import com.jfaf.irc.domain.config.AppConfig
import com.jfaf.irc.domain.notifications.Notifier
import com.jfaf.irc.domain.platform.AdProvider
import com.jfaf.irc.domain.platform.AnalyticsTracker
import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.platform.ConnectionHost
import com.jfaf.irc.domain.platform.UpdateChecker
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.domain.user.UserMetadataStore
import com.jfaf.irc.service.AndroidConnectionHost
import com.jfaf.irc.service.AndroidNotifier
import com.jfaf.irc.util.BuildConfigAppConfig
import org.koin.android.ext.koin.androidContext
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module

/** Implementaciones Android: Context, Room, DataStore, Firebase, AdMob y servicio. */
val androidModule = module {
    // Persistencia
    single {
        val dbFile = androidContext().getDatabasePath(CHAT_DATABASE_NAME)
        buildChatDatabase(Room.databaseBuilder<ChatDatabase>(androidContext(), dbFile.absolutePath))
    }
    factory { get<ChatDatabase>().chatHistoryDao() }
    single<DataStore<Preferences>> {
        createPreferencesDataStore(androidContext().filesDir.resolve("datastore/$USER_PREFERENCES_FILE").absolutePath)
    }

    // Plataforma
    factory<IrcTransport> { JvmSocketTransport() } // uno nuevo por intento de conexión
    // Se crea al primer uso (al crear el ViewModel): lee una vez los textos de Compose Resources.
    single<StringProvider> { runBlocking { ComposeStringProvider.load() } }
    single<AppConfig> { BuildConfigAppConfig() }
    single<ConnectionHost> { AndroidConnectionHost(androidContext()) }
    single<Notifier> { AndroidNotifier(androidContext()) }
    single<AdProvider> { NativeAdManager(androidContext()) }

    // Firebase
    single { FirebaseAnalytics.getInstance(androidContext()) }
    single<AnalyticsTracker> { FirebaseAnalyticsTracker(get()) }
    single { Firebase.auth }
    single { Firebase.firestore }
    single<AuthSession> { FirebaseAuthSession(get()) }
    single<UserMetadataStore> { FirestoreUserMetadataStore(get(), get()) }
    single<UpdateChecker> { RemoteConfigManager(androidContext()) }
}
