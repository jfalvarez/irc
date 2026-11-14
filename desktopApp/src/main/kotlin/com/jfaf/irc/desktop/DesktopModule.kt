package com.jfaf.irc.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.jfaf.irc.data.auth.FirebaseAuthApi
import com.jfaf.irc.data.auth.FirebaseRestAuth
import com.jfaf.irc.data.auth.RestAuthSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import com.jfaf.irc.data.history.room.CHAT_DATABASE_NAME
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.data.history.room.buildChatDatabase
import com.jfaf.irc.data.prefs.USER_PREFERENCES_FILE
import com.jfaf.irc.data.prefs.createPreferencesDataStore
import com.jfaf.irc.data.remote.IrcTransport
import com.jfaf.irc.data.remote.JvmSocketTransport
import com.jfaf.irc.data.text.ComposeStringProvider
import com.jfaf.irc.data.firestore.FirestoreRestUserLists
import com.jfaf.irc.data.user.LocalUserMetadataStore
import com.jfaf.irc.data.user.SyncedUserMetadataStore
import com.jfaf.irc.domain.platform.AppVisibility
import com.jfaf.irc.domain.user.RemoteUserLists
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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
import com.jfaf.irc.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

/**
 * Implementaciones de escritorio. Junto con `commonModule` y `viewModelModule` (de `:shared`)
 * completa el grafo de Koin. [dataDir] es la carpeta de datos del usuario, [soundOutput] el que emite el sonido
 * de aviso (las pruebas ponen uno que sólo cuenta) y [google] la configuración del login de Google
 * (por defecto, la de `local.properties`; null: sin login, como en las pruebas).
 */
fun desktopModule(
    dataDir: File,
    soundOutput: SoundPlayer = ToneSoundPlayer(),
    google: DesktopGoogleConfig? = DesktopGoogleConfig.fromBuild()
) = module {
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
    single { SoundAlertsSetting(get(), get<CoroutineScope>(ApplicationScope)) }
    single<SoundPlayer> {
        val setting = get<SoundAlertsSetting>()
        // Primero el interruptor y luego el limitador: un aviso silenciado no gasta el intervalo.
        PreferenceGatedSoundPlayer(RateLimitedSoundPlayer(soundOutput)) { setting.enabled.value }
    }
    single { DesktopNotifier(get()) } bind Notifier::class
    single<Haptics> { DesktopHaptics(get()) }
    single<AdProvider> { NoAdProvider() }
    single<AnalyticsTracker> { NoOpAnalyticsTracker() }
    if (google == null) {
        single<AuthSession> { NoAuthSession() }
        single<UserMetadataStore> { LocalUserMetadataStore(get()) }
    } else {
        // Login de Google: navegador del sistema + Firebase Auth por REST; sesión cifrada en la carpeta de datos
        val now = { System.currentTimeMillis() }
        single { HttpClient(OkHttp) }
        single<FirebaseAuthApi> { FirebaseRestAuth(get(), google.firebaseApiKey, now) }
        single { RestAuthSession(get(), DpapiSessionStore(File(dataDir, SESSION_FILE)), now).also { it.start(get(ApplicationScope)) } } bind AuthSession::class
        single { GoogleLoopbackOAuth(get(), google.clientId, google.clientSecret) }
        single { DesktopGoogleSignIn(get(), get(), get(ApplicationScope)) }

        // Listas: en local y, con sesión, sincronizadas con Firestore por REST (como Android, sin escucha en
        // tiempo real: también se consulta al volver a la ventana)
        single<RemoteUserLists> {
            val auth = get<RestAuthSession>()
            val backToWindow = get<AppVisibility>().isInForeground.drop(1).filter { it }.map { }
            FirestoreRestUserLists(get(), google.firebaseProjectId, auth, auth::idToken, now, refreshRequests = backToWindow)
        }
        single<UserMetadataStore> {
            SyncedUserMetadataStore(LocalUserMetadataStore(get()), get()).also { it.start(get(ApplicationScope)) }
        }
    }
    single<UpdateChecker> { NoUpdateChecker() }
}

/** Sesión de Google guardada (cifrada con DPAPI en Windows), en la carpeta de datos. */
const val SESSION_FILE = "google_session.bin"
