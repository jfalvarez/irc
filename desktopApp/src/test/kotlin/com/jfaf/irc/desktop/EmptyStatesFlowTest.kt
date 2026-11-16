package com.jfaf.irc.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.remote.IrcTransport
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import com.jfaf.irc.domain.terms.TermsOfUse
import com.jfaf.irc.ui.App
import com.jfaf.irc.ui.screens.chat.NO_CHANNELS_BROWSE_TAG
import com.jfaf.irc.ui.viewmodels.MainViewModel
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** UI-37: las pistas de los estados vacíos: «ningún canal» al conectar y canales o privados sin mensajes. */
@OptIn(ExperimentalTestApi::class)
class EmptyStatesFlowTest {

    private lateinit var dataDir: File
    private lateinit var koin: Koin
    private lateinit var server: ScriptedTransport

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-empty-test").toFile()
        server = ScriptedTransport()
        koin = startKoin {
            modules(
                commonModule, viewModelModule, desktopModule(dataDir),
                module { factory<IrcTransport> { server } }
            )
        }.koin
        runBlocking { koin.get<UserPreferencesRepository>().acceptTerms(TermsOfUse.CURRENT_VERSION) }
    }

    @AfterTest
    fun tearDown() {
        runCatching { koin.get<ChatDatabase>().close() }
        stopKoin()
        dataDir.deleteRecursively()
    }

    private fun SkikoComposeUiTest.connect(vm: MainViewModel) {
        setContent {
            App(platformUi = DesktopPlatformUi(), onRedirectToPlayStore = {}, onExit = {}, mainViewModel = vm)
        }
        server.serverSays(":srv 001 Tester :Welcome")
        vm.connect("Tester", false, null, false)
        waitUntil(timeoutMillis = 10_000) { vm.chatScreenState.connectionState.value }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.count(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun sinCanales_elServidorOfreceExplorarCanales() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connect(vm)

        onNodeWithText("Todavía no estás en ningún canal").assertExists()
        onNodeWithTag(NO_CHANNELS_BROWSE_TAG).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { count("Buscar por nombre o tema") > 0 }
    }

    @Test
    fun alEntrarEnUnCanal_elAvisoDeNingunCanalDesaparece() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connect(vm)
        onNodeWithText("Todavía no estás en ningún canal").assertExists()

        vm.joinChannel("#test")
        server.serverSays(
            ":Tester!u@h JOIN :#test",
            ":srv 353 Tester = #test :Tester",
            ":srv 366 Tester #test :End of /NAMES list."
        )
        waitUntil(timeoutMillis = 10_000) { vm.chatScreenState.chatTargets.value.contains("#test") }
        // Se vuelve al Servidor: con un canal abierto ya no se muestra el aviso
        vm.setActiveTarget("Servidor")
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { count("Todavía no estás en ningún canal") == 0 }
    }

    @Test
    fun unCanalSinMensajes_diceQueEscribaElPrimero_yDejaDeDecirloConElPrimero() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connect(vm)
        vm.joinChannel("#test")
        server.serverSays(
            ":Tester!u@h JOIN :#test",
            ":srv 353 Tester = #test :Tester Alice",
            ":srv 366 Tester #test :End of /NAMES list."
        )
        waitUntil(timeoutMillis = 10_000) { vm.chatScreenState.activeTarget.value == "#test" }
        waitUntil(timeoutMillis = 5_000) { count("Aún no hay mensajes en #test") > 0 }

        server.serverSays(":Alice!u@h PRIVMSG #test :hola")
        waitUntil(timeoutMillis = 5_000) { count("Aún no hay mensajes en #test") == 0 }
        onNode(hasText("hola")).assertExists()
    }

    @Test
    fun unPrivadoSinMensajes_tambienTienePista() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connect(vm)
        vm.openPrivateMessage("ana")
        waitUntil(timeoutMillis = 10_000) { vm.chatScreenState.activeTarget.value == "ana" }
        waitUntil(timeoutMillis = 5_000) { count("Aún no hay mensajes con ana") > 0 }
    }
}
