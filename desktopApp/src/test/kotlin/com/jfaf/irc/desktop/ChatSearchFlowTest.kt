package com.jfaf.irc.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.remote.IrcTransport
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import com.jfaf.irc.domain.terms.TermsOfUse
import com.jfaf.irc.ui.App
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
import kotlin.test.assertTrue

/** UI-35: la búsqueda dentro del chat de punta a punta: abrir, buscar, moverse entre resultados y cerrar. */
@OptIn(ExperimentalTestApi::class)
class ChatSearchFlowTest {

    private lateinit var dataDir: File
    private lateinit var koin: Koin
    private lateinit var server: ScriptedTransport

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-search-test").toFile()
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

    /** Conecta, entra en #test y recibe unos mensajes de otros. */
    private fun SkikoComposeUiTest.connectAndChat(vm: MainViewModel) {
        setContent {
            App(platformUi = DesktopPlatformUi(), onRedirectToPlayStore = {}, onExit = {}, mainViewModel = vm)
        }
        server.serverSays(":srv 001 Tester :Welcome")
        vm.connect("Tester", false, null, false)
        waitUntil(timeoutMillis = 10_000) { vm.chatScreenState.connectionState.value }
        vm.joinChannel("#test")
        server.serverSays(
            ":Tester!u@h JOIN :#test",
            ":srv 353 Tester = #test :Tester Alice Bob",
            ":srv 366 Tester #test :End of /NAMES list.",
            ":Alice!u@h PRIVMSG #test :hola mundo",
            ":Bob!u@h PRIVMSG #test :Música buena",
            ":Alice!u@h PRIVMSG #test :adiós, mundo cruel"
        )
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("adiós, mundo cruel", substring = true).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.openSearchFromMenu() {
        onNodeWithContentDescription("Más opciones").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNode(hasText("Buscar en el chat") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    @Test
    fun buscarContaLosResultados_yPasarDeUnoAOtro() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndChat(vm)

        openSearchFromMenu()
        onNodeWithText("Buscar en este chat").assertExists()

        onAllNodes(hasSetTextAction()).onFirst().performTextInput("mundo")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("1 de 2").fetchSemanticsNodes().isNotEmpty() }

        // Del más reciente al más antiguo; al llegar al último, da la vuelta
        onNodeWithContentDescription("Resultado más antiguo").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("2 de 2").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Resultado más antiguo").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("1 de 2").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Resultado más reciente").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("2 de 2").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun noDistingueAcentos_yAvisaSiNoHayResultados() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndChat(vm)

        openSearchFromMenu()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("musica")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("1 de 1").fetchSemanticsNodes().isNotEmpty() }

        onAllNodes(hasSetTextAction()).onFirst().performTextInput("zzz")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Sin resultados").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun elResultadoLejano_seMuestraAlBuscarlo() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndChat(vm)
        // Una aguja antigua bajo un pajar de mensajes: queda muy por encima de lo que cabe en pantalla
        server.serverSays(":Bob!u@h PRIVMSG #test :la aguja perdida")
        server.serverSays(*Array(80) { ":Alice!u@h PRIVMSG #test :relleno número $it" })
        waitUntil(timeoutMillis = 10_000) { vm.chatScreenState.uiMessages.value.size >= 84 }
        waitForIdle()
        // Una ráfaga tan grande deja la lista en mitad del historial: se baja del todo con el botón de «ir al final»
        onNodeWithContentDescription("Ir al último mensaje").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("relleno número 79", substring = true).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        assertTrue(onAllNodesWithText("aguja perdida", substring = true).fetchSemanticsNodes().isEmpty(), "La aguja no debería verse todavía")

        openSearchFromMenu()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("aguja")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("aguja perdida", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("1 de 1").assertExists()
    }

    @Test
    fun cerrarLaBusqueda_devuelveLaBarraDelChat() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndChat(vm)

        openSearchFromMenu()
        onNodeWithText("Buscar en este chat").assertExists()
        onNodeWithContentDescription("Cerrar la búsqueda").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithText("Buscar en este chat").assertDoesNotExist()
        onNodeWithContentDescription("Más opciones").assertExists()
    }

    @Test
    fun ctrlF_abreLaBusqueda_yEscLaCierra() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndChat(vm)

        onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        waitForIdle()
        onNode(hasSetTextAction()).performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.F)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
        onNodeWithText("Buscar en este chat").assertExists()

        onAllNodes(hasSetTextAction()).onFirst().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertTrue(onAllNodesWithText("Buscar en este chat").fetchSemanticsNodes().isEmpty(), "Esc debe cerrar la búsqueda")
    }
}
