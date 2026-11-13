package com.jfaf.irc.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
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
import com.jfaf.irc.domain.platform.Haptics
import com.jfaf.irc.domain.platform.UpdateChecker
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.domain.user.UserMetadataStore
import kotlinx.coroutines.runBlocking
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

/**
 * Implementaciones de escritorio. Junto con `commonModule` y `viewModelModule` (de `:shared`)
 * completa el grafo de Koin. [dataDir] es la carpeta de datos del usuario.
 */
fun desktopModule(dataDir: File) = module {
    // Persistencia: las mismas funciones comunes que Android, con rutas de archivo
    single {
        buildChatDatabase(Room.databaseBuilder<ChatDatabase>(name = File(dataDir, CHAT_DATABASE_NAME).absolutePath))
    }
    factory { get<ChatDatabase>().chatHistoryDao() }
    single<DataStore<Preferences>> {
        createPreferencesDataStore(File(dataDir, "datastore/$USER_PREFERENCES_FILE").absolutePath)
    }

    // Plataforma
    factory<IrcTransport> { JvmSocketTransport() } // uno nuevo por intento de conexión
    single<StringProvider> { runBlocking { ComposeStringProvider.load() } }
    single<AppConfig> { DesktopAppConfig() }
    single<ConnectionHost> { DesktopConnectionHost() }
    single { DesktopNotifier() } bind Notifier::class
    single<Haptics> { NoOpHaptics() }
    single<AdProvider> { NoAdProvider() }
    single<AnalyticsTracker> { NoOpAnalyticsTracker() }
    single<AuthSession> { NoAuthSession() }
    single<UserMetadataStore> { LocalUserMetadataStore(get()) }
    single<UpdateChecker> { NoUpdateChecker() }
}
