package com.jfaf.irc.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
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
import kotlin.test.assertFalse

/**
 * #27: el autocompletado de nicks de punta a punta (servidor de pega → `NAMES` → ViewModel), con la ventana estrecha
 * y la lista de usuarios cerrada, que es el caso normal en un móvil. Nadie observa entonces la lista de usuarios.
 */
@OptIn(ExperimentalTestApi::class)
class NickAutocompleteFlowTest {

    private lateinit var dataDir: File
    private lateinit var koin: Koin
    private lateinit var server: ScriptedTransport

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-nick-test").toFile()
        server = ScriptedTransport()
        koin = startKoin {
            modules(
                commonModule, viewModelModule, desktopModule(dataDir),
                module { factory<IrcTransport> { server } }
            )
        }.koin
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
            App(platformUi = DesktopPlatformUi, onGoogleSignIn = {}, onRedirectToPlayStore = {}, onExit = {}, mainViewModel = vm)
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

    @Test
    fun sugiereNicksConLaListaDeUsuariosCerrada() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)

        vm.updateNickSuggestions("hola ca", 7)

        assertEquals(listOf("carl"), vm.chatScreenState.nickSuggestions.value)
    }

    @Test
    fun sugiereOperadoresYVocesSinElPrefijo() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)

        vm.updateNickSuggestions("hola al", 7)
        assertEquals(listOf("Alice"), vm.chatScreenState.nickSuggestions.value)

        vm.updateNickSuggestions("bo", 2)
        assertEquals(listOf("Bob"), vm.chatScreenState.nickSuggestions.value)
    }
}
