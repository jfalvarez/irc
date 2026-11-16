package com.jfaf.irc.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
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

/** UI-30: el selector rápido (Ctrl+K) de punta a punta: abrir, buscar y saltar a un canal, y unirse a uno nuevo. */
@OptIn(ExperimentalTestApi::class)
class QuickSwitcherFlowTest {

    private lateinit var dataDir: File
    private lateinit var koin: Koin
    private lateinit var server: ScriptedTransport

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-quick-test").toFile()
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

    /** Pone el foco en el campo de mensajes, que es donde está el usuario al pulsar el atajo. */
    private fun SkikoComposeUiTest.focusMessageInput() {
        onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        waitForIdle()
    }

    /** Pulsa Ctrl+K en el campo con foco: el último que haya (con el selector abierto, el suyo va delante del de mensajes). */
    private fun SkikoComposeUiTest.pressCtrlK() {
        onAllNodes(hasSetTextAction()).onLast().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.K)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.openSwitcher() = pressCtrlK()

    private fun SkikoComposeUiTest.joinSecondChannel(vm: MainViewModel) {
        vm.joinChannel("#dev-kotlin")
        server.serverSays(
            ":Tester!u@h JOIN :#dev-kotlin",
            ":srv 353 Tester = #dev-kotlin :Tester",
            ":srv 366 Tester #dev-kotlin :End of /NAMES list."
        )
        waitUntil(timeoutMillis = 10_000) { vm.chatScreenState.chatTargets.value.contains("#dev-kotlin") }
        // Vuelve al primero: así hay otra pestaña a la que saltar
        vm.setActiveTarget("#test")
        waitForIdle()
    }

    @Test
    fun ctrlK_abreElSelector_yBuscarSaltaAlCanal() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)
        focusMessageInput()
        joinSecondChannel(vm)
        assertEquals("#test", vm.chatScreenState.activeTarget.value)

        fun clickable(text: String) = onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().size
        val devBefore = clickable("#dev-kotlin")
        val testBefore = clickable("#test")

        openSwitcher()
        onNodeWithText("Ir a un canal o conversación…").assertExists()

        // Sin texto salen las demás pestañas; la activa (#test) no
        assertEquals(devBefore + 1, clickable("#dev-kotlin"), "El selector debe ofrecer #dev-kotlin")
        assertEquals(testBefore, clickable("#test"), "El selector no debe ofrecer la pestaña activa")


        onAllNodes(hasText("#dev-kotlin") and hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        assertEquals("#dev-kotlin", vm.chatScreenState.activeTarget.value)
        onNodeWithText("Ir a un canal o conversación…").assertDoesNotExist()
    }

    @Test
    fun escribirUnCanalNuevo_ofreceUnirse() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)
        focusMessageInput()

        openSwitcher()
        onAllNodes(hasSetTextAction()).onLast().performTextInput("#nuevo")
        waitForIdle()

        onNodeWithText("Unirse al canal").assertExists()
        onAllNodes(hasText("#nuevo") and hasClickAction()).onLast().assertExists()
    }

    @Test
    fun ctrlKDeNuevo_loCierra() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)
        focusMessageInput()

        openSwitcher()
        onNodeWithText("Ir a un canal o conversación…").assertExists()
        // El campo del selector debe recibir el foco solo al abrirse
        onAllNodes(hasSetTextAction()).onLast().assertIsFocused()
        openSwitcher()
        onNodeWithText("Ir a un canal o conversación…").assertDoesNotExist()
    }

    @Test
    fun laLupaDeLaBarraAbreElSelector_sinTeclado() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)

        onNodeWithContentDescription("Buscar canal o conversación").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        onNodeWithText("Ir a un canal o conversación…").assertExists()
    }

    @Test
    fun laFilaBuscarDelPanel_enseniaElAtajo_yAbreElSelector() = runSkikoComposeUiTest(size = Size(1100f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)

        // Ventana ancha de escritorio: el panel de pestañas es fijo y la fila lleva la etiqueta del atajo
        onNodeWithText("Ctrl+K", substring = true).assertExists()
        onNode(hasText("Buscar…", substring = true) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        onNodeWithText("Ir a un canal o conversación…").assertExists()
    }
}
