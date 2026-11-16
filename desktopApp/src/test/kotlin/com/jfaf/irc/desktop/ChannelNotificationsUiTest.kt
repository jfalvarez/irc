package com.jfaf.irc.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jfaf.irc.domain.notifications.ChannelNotificationLevel
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.remote.IrcTransport
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import com.jfaf.irc.domain.terms.TermsOfUse
import com.jfaf.irc.ui.App
import com.jfaf.irc.ui.viewmodels.MainViewModel
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** UI-32: el nivel de aviso de un canal desde el menú ⋮ (diálogo, guardado y marca en el cajón). */
@OptIn(ExperimentalTestApi::class)
class ChannelNotificationsUiTest {

    private lateinit var dataDir: File
    private lateinit var koin: Koin
    private lateinit var server: ScriptedTransport

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-chnotif-test").toFile()
        server = ScriptedTransport()
        koin = startKoin {
            modules(
                commonModule, viewModelModule, desktopModule(dataDir),
                module { factory<IrcTransport> { server } }
            )
        }.koin
        // Condiciones ya aceptadas: si no, su diálogo taparía la app
        runBlocking { koin.get<UserPreferencesRepository>().acceptTerms(TermsOfUse.CURRENT_VERSION) }
    }

    @AfterTest
    fun tearDown() {
        runCatching { koin.get<ChatDatabase>().close() }
        stopKoin()
        dataDir.deleteRecursively()
    }

    /** Conecta, entra en #test y recibe su lista de nombres (con prefijos de modo, como manda un servidor real). */
    private fun SkikoComposeUiTest.connectAndReceiveNames(vm: MainViewModel) {
        setContent {
            App(platformUi = DesktopPlatformUi(), onRedirectToPlayStore = {}, onExit = {}, mainViewModel = vm)
        }
        server.serverSays(":srv 001 Tester :Welcome")
        vm.connect("Tester", false, null, false)
        waitUntil(timeoutMillis = 10_000) { vm.chatScreenState.connectionState.value }
        vm.joinChannel("#test")
        server.serverSays(
            ":Tester!u@h JOIN :#test",
            ":srv 353 Tester = #test :Tester @Alice +Bob carl",
            ":srv 366 Tester #test :End of /NAMES list."
        )
        waitUntil(timeoutMillis = 10_000) {
            vm.chatScreenState.activeTarget.value == "#test" &&
                vm.chatScreenState.usersInChannel.value["#test"].orEmpty().any { it.endsWith("carl") }
        }
        waitForIdle()
        assertFalse(vm.chatScreenState.showUserList.value, "La lista de usuarios debe estar cerrada en este caso")
    }

    private fun SkikoComposeUiTest.openChannelNotifications() {
        onNodeWithContentDescription("Más opciones").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNode(hasText("Notificaciones:", substring = true) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    private fun levelOf(vm: MainViewModel) = vm.channelNotificationLevels.value["#test"]

    @Test
    fun elMenuMuestraElNivelPorDefecto() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)

        onNodeWithContentDescription("Más opciones").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithText("Notificaciones: Solo menciones").assertExists()
    }

    @Test
    fun elegirSilenciado_loGuarda_yElMenuLoMuestra() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)

        openChannelNotifications()
        onNodeWithText("Notificaciones de #test").assertExists()
        onNode(hasText("Silenciado") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { levelOf(vm) == ChannelNotificationLevel.MUTED }
        waitForIdle()
        onNodeWithText("Notificaciones de #test").assertDoesNotExist()

        onNodeWithContentDescription("Más opciones").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithText("Notificaciones: Silenciado").assertExists()
    }

    @Test
    fun elegirTodo_yVolverAlPredeterminado_loQuitaDelAlmacen() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)

        openChannelNotifications()
        onNode(hasText("Todos los mensajes") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { levelOf(vm) == ChannelNotificationLevel.ALL }

        openChannelNotifications()
        onNode(hasText("Solo menciones") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { levelOf(vm) == null }
        assertTrue(vm.channelNotificationLevels.value.isEmpty())
    }

    @Test
    fun unCanalSilenciadoLlevaLaCampanaTachadaEnElCajon() = runSkikoComposeUiTest(size = Size(1100f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)
        onAllNodesWithContentDescription("Canal silenciado").assertCountEquals(0)

        vm.setChannelNotificationLevel("#test", ChannelNotificationLevel.MUTED)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Canal silenciado").fetchSemanticsNodes().isNotEmpty() }
    }
}
