package com.jfaf.irc.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.data.remote.IrcTransport
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import com.jfaf.irc.ui.App
import com.jfaf.irc.ui.viewmodels.MainViewModel
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La UI de escritorio completa (`App` + `DesktopPlatformUi`) contra un servidor IRC de pega, sin red:
 * ventana ancha (paneles fijos) frente a estrecha (cajón) y atajos de teclado.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopLayoutTest {

    private lateinit var dataDir: File
    private lateinit var koin: Koin
    private lateinit var server: ScriptedTransport

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-layout-test").toFile()
        server = ScriptedTransport()
        // Koin global: App usa koinInject y koinViewModel
        koin = startKoin {
            modules(
                commonModule, viewModelModule, desktopModule(dataDir),
                module { factory<IrcTransport> { server } } // sustituye el transporte real
            )
        }.koin
    }

    @AfterTest
    fun tearDown() {
        runCatching { koin.get<ChatDatabase>().close() }
        stopKoin()
        dataDir.deleteRecursively()
    }

    /** Conecta y entra en #test, que queda como pestaña activa. */
    private fun SkikoComposeUiTest.connectAndJoin(vm: MainViewModel) {
        server.serverSays(":srv 001 Tester :Welcome")
        vm.connect("Tester", false, null, false)
        waitUntil(timeoutMillis = 10_000) { vm.chatScreenState.connectionState.value }
        vm.joinChannel("#test")
        server.serverSays(":Tester!u@h JOIN :#test")
        waitUntil(timeoutMillis = 10_000) { vm.chatScreenState.activeTarget.value == "#test" }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.showApp(vm: MainViewModel) = setContent {
        App(platformUi = DesktopPlatformUi, onGoogleSignIn = {}, onRedirectToPlayStore = {}, onExit = {}, mainViewModel = vm)
    }

    private fun SkikoComposeUiTest.count(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun ventanaAnchaMuestraPestanasYUsuariosSinCajon() = runSkikoComposeUiTest(size = Size(1200f, 760f)) {
        val vm = koin.get<MainViewModel>()
        showApp(vm)
        connectAndJoin(vm)

        // Sin botón de cajón ni de lista de usuarios: ambos paneles son fijos
        assertEquals(0, onAllNodesWithContentDescription("Abrir menú lateral").fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithContentDescription("Mostrar/ocultar lista de usuarios").fetchSemanticsNodes().size)
        // Panel izquierdo (pestañas) y derecho (usuarios del canal; aún vacío)
        assertTrue(count("Canales y Chats") > 0, "Debe verse el panel de pestañas")
        assertTrue(count("No hay usuarios en este canal") > 0, "Debe verse el panel de usuarios")
    }

    @Test
    fun ventanaEstrechaVuelveAlCajonYOcultaLosUsuarios() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        showApp(vm)
        connectAndJoin(vm)

        assertEquals(1, onAllNodesWithContentDescription("Abrir menú lateral").fetchSemanticsNodes().size)
        assertEquals(1, onAllNodesWithContentDescription("Mostrar/ocultar lista de usuarios").fetchSemanticsNodes().size)
        assertEquals(0, count("No hay usuarios en este canal"), "Sin pulsar el botón no hay lista de usuarios")
    }

    @Test
    fun ctrlTabCambiaDePestanaYCtrlWCierraLaActiva() = runSkikoComposeUiTest(size = Size(1200f, 760f)) {
        val vm = koin.get<MainViewModel>()
        showApp(vm)
        connectAndJoin(vm)
        assertEquals("#test", vm.chatScreenState.activeTarget.value)

        // El campo de texto tiene el foco solo: los atajos llegan desde él
        onRoot().performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Tab); keyUp(Key.CtrlLeft) }
        waitForIdle()
        assertEquals("Servidor", vm.chatScreenState.activeTarget.value)

        onRoot().performKeyInput { keyDown(Key.CtrlLeft); keyDown(Key.ShiftLeft); pressKey(Key.Tab); keyUp(Key.ShiftLeft); keyUp(Key.CtrlLeft) }
        waitForIdle()
        assertEquals("#test", vm.chatScreenState.activeTarget.value)

        onRoot().performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.W); keyUp(Key.CtrlLeft) }
        waitUntil(timeoutMillis = 5_000) { server.sent.any { it.startsWith("PART #test") } }
    }
}
