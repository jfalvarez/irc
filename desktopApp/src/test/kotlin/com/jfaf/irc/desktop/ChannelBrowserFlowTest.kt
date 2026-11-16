package com.jfaf.irc.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
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

/** El explorador de canales de punta a punta: pedir `LIST`, ver la lista, buscar y unirse (con y sin clave). */
@OptIn(ExperimentalTestApi::class)
class ChannelBrowserFlowTest {

    private lateinit var dataDir: File
    private lateinit var koin: Koin
    private lateinit var server: ScriptedTransport

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-channels-test").toFile()
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

    private fun SkikoComposeUiTest.connectAndJoinTest(vm: MainViewModel) {
        setContent {
            App(platformUi = DesktopPlatformUi(), onRedirectToPlayStore = {}, onExit = {}, mainViewModel = vm)
        }
        server.serverSays(":srv 001 Tester :Welcome")
        vm.connect("Tester", false, null, false)
        waitUntil(timeoutMillis = 10_000) { vm.chatScreenState.connectionState.value }
        vm.joinChannel("#test")
        server.serverSays(
            ":Tester!u@h JOIN :#test",
            ":srv 353 Tester = #test :Tester",
            ":srv 366 Tester #test :End of /NAMES list."
        )
        waitUntil(timeoutMillis = 10_000) { vm.chatScreenState.activeTarget.value == "#test" }
        waitForIdle()
    }

    /** Abre el explorador desde el menú ⋮ y responde al `LIST` como un servidor. */
    private fun SkikoComposeUiTest.openBrowserAndReceiveList() {
        onNodeWithContentDescription("Más opciones").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        // La fila del cajón también se llama así (compuesta aunque el cajón esté cerrado): la del menú es la última
        onAllNodes(hasText("Explorar canales")).onLast().performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 10_000) { server.sent.any { it.startsWith("LIST") } }
        server.serverSays(
            ":srv 321 Tester Channel :Users  Name",
            ":srv 322 Tester #test 1 :[+nt] El canal donde estoy",
            ":srv 322 Tester #hispano 120 :[+nt] Bienvenidos al canal",
            ":srv 322 Tester #kotlin 30 :[+nt] Programación en Kotlin",
            ":srv 322 Tester #privado 4 :[+ntk] Sólo con clave",
            ":srv 323 Tester :End of /LIST"
        )
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("#hispano")).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
    }

    @Test
    fun desdeElMenu_pideLaListaYLaMuestra() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndJoinTest(vm)
        openBrowserAndReceiveList()

        onNodeWithText("4 canales").assertExists()
        // Por usuarios, de más a menos, con el tema limpio y el contador
        onNodeWithText("120 usuarios").assertExists()
        onNodeWithText("Bienvenidos al canal").assertExists()
        // Ya estoy en #test: lleva la marca de abierto
        onNodeWithText("Abierto").assertExists()
    }

    @Test
    fun buscarFiltraPorNombreYTema_yUnirseEnviaJoin() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndJoinTest(vm)
        openBrowserAndReceiveList()

        onNode(hasSetTextAction()).performTextInput("programación")
        waitForIdle()
        onNodeWithText("1 de 4 canales").assertExists()
        onAllNodes(hasText("#hispano")).fetchSemanticsNodes().let { assertTrue(it.isEmpty(), "#hispano debería estar filtrado") }

        onNode(hasText("#kotlin") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 10_000) { server.sent.any { it.startsWith("JOIN #kotlin") } }
        // Tras unirse, vuelve al chat
        waitForIdle()
        onNodeWithText("Buscar por nombre o tema").assertDoesNotExist()
    }

    @Test
    fun soloSenuelos_avisaYNoMuestraFilas() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndJoinTest(vm)
        onNodeWithContentDescription("Más opciones").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onAllNodes(hasText("Explorar canales")).onLast().performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 10_000) { server.sent.any { it.startsWith("LIST") } }
        server.serverSays(
            ":srv 321 Tester Channel :Users  Name",
            ":srv 322 Tester #cIF35JjzKlwxXWne 3143 :Fake channel for confusing spambots",
            ":srv 322 Tester #F85 2056 :Fake channel for confusing spambots",
            ":srv 323 Tester :End of /LIST"
        )
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText("canales señuelo", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Reintentar").assertExists()
        assertTrue(onAllNodes(hasText("#F85")).fetchSemanticsNodes().isEmpty(), "Los señuelos no deben salir en la lista")
    }

    @Test
    fun canalConClave_pideLaClaveAntesDeUnirse() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndJoinTest(vm)
        openBrowserAndReceiveList()

        onNode(hasText("#privado") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithText("Clave de #privado").assertExists()
        assertTrue(server.sent.none { it.startsWith("JOIN #privado") }, "No debe unirse sin la clave")

        onAllNodes(hasSetTextAction()).fetchSemanticsNodes() // el diálogo tiene su campo
        onNodeWithText("Clave del canal").assertExists()
    }
}
