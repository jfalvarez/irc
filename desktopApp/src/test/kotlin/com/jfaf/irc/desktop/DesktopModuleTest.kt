package com.jfaf.irc.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.data.remote.IrcSession
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import com.jfaf.irc.domain.IrcConnection
import com.jfaf.irc.domain.IrcSender
import com.jfaf.irc.data.history.ChatHistoryStore
import com.jfaf.irc.domain.notifications.Notifier
import com.jfaf.irc.domain.platform.AdProvider
import com.jfaf.irc.domain.platform.AnalyticsTracker
import com.jfaf.irc.domain.platform.AppVisibility
import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.platform.ConnectionHost
import com.jfaf.irc.domain.platform.Haptics
import com.jfaf.irc.domain.platform.UpdateChecker
import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.domain.user.UserMetadataStore
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.ui.viewmodels.SettingsViewModel
import com.jfaf.irc.ui.viewmodels.SignInViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Un `get()` sin definición en Koin sólo falla al ejecutar: este test arranca el grafo completo
 * de escritorio (`commonModule` + `viewModelModule` + `desktopModule`) y resuelve todo lo que pide.
 */
class DesktopModuleTest {

    private lateinit var dataDir: File
    private lateinit var koin: Koin
    private lateinit var app: org.koin.core.KoinApplication

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-desktop-test").toFile()
        app = koinApplication { modules(commonModule, viewModelModule, desktopModule(dataDir)) }
        koin = app.koin
    }

    @AfterTest
    fun tearDown() {
        runCatching { koin.get<ChatDatabase>().close() }
        app.close()
        dataDir.deleteRecursively()
    }

    @Test
    fun resuelveLasInterfacesDePlataforma() {
        assertNotNull(koin.get<ConnectionHost>())
        assertNotNull(koin.get<Notifier>())
        assertNotNull(koin.get<Haptics>())
        assertNotNull(koin.get<AdProvider>())
        assertNotNull(koin.get<AnalyticsTracker>())
        assertNotNull(koin.get<AuthSession>())
        assertNotNull(koin.get<UpdateChecker>())
        assertNotNull(koin.get<AppVisibility>())
        assertNotNull(koin.get<IrcSession>())
        assertNotNull(koin.get<IrcConnection>())
        assertNotNull(koin.get<IrcSender>())
        assertTrue(koin.get<Notifier>() === koin.get<DesktopNotifier>(), "El Notifier debe ser el DesktopNotifier")
    }

    @Test
    fun resuelveLosTextosDeComposeResources() {
        val text = koin.get<StringProvider>().get(Msg.SNACK_DISCONNECTED)
        assertTrue(text.isNotBlank())
    }

    @Test
    fun resuelveLasBasesDeDatosEnLaCarpetaDeDatos() {
        assertNotNull(koin.get<DataStore<Preferences>>())
        assertNotNull(koin.get<ChatHistoryStore>())
        runBlocking { koin.get<ChatHistoryStore>().load() }
        assertTrue(File(dataDir, "chat_history.db").exists(), "Room debe crear la base en la carpeta de datos")
    }

    @Test
    fun resuelveLosViewModels() {
        assertNotNull(koin.get<MainViewModel>())
        assertNotNull(koin.get<SettingsViewModel>())
        assertNotNull(koin.get<SignInViewModel>())
    }

    @Test
    fun sinLoginNoHayUsuario() {
        assertNull(koin.get<AuthSession>().currentUser.value)
    }

    @Test
    fun ignoradosYAmigosSeGuardanEnLocalEnMinusculas() = runBlocking {
        val store = koin.get<UserMetadataStore>()
        store.addIgnoredUser("PePe")
        store.addFriend("Ana")
        assertEquals(setOf("pepe"), store.ignoredUsersFlow.first())
        assertEquals(setOf("ana"), store.friendsFlow.first())
        store.removeIgnoredUser("PEPE")
        assertEquals(emptySet(), store.ignoredUsersFlow.first())
    }
}
