package com.jfaf.irc.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.remote.IrcTransport
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import com.jfaf.irc.domain.terms.TermsOfUse
import com.jfaf.irc.domain.linkpreview.FetchedPage
import com.jfaf.irc.domain.linkpreview.LinkPreviewFetcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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

/** UI-31: la vista previa de enlaces de punta a punta (servidor de pega, descargador de pega): apagada no pide nada, activada pinta la tarjeta. */
@OptIn(ExperimentalTestApi::class)
class LinkPreviewFlowTest {

    private lateinit var dataDir: File
    private lateinit var koin: Koin
    private lateinit var server: ScriptedTransport
    private val fetcher = FakeFetcher()

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-linkpreview-test").toFile()
        server = ScriptedTransport()
        koin = startKoin {
            modules(
                commonModule, viewModelModule, desktopModule(dataDir),
                module { factory<IrcTransport> { server }; single<LinkPreviewFetcher> { fetcher } }
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

    private class FakeFetcher : LinkPreviewFetcher {
        val calls = mutableListOf<String>()
        override suspend fun fetch(url: String): FetchedPage? {
            synchronized(calls) { calls += url }
            return FetchedPage(
                url,
                "<head><meta property=\"og:title\" content=\"Titular de prueba\"><meta property=\"og:description\" content=\"Resumen de la web\"></head>"
            )
        }
        fun count() = synchronized(calls) { calls.size }
    }

    private fun SkikoComposeUiTest.receiveLink() {
        server.serverSays(":ana!u@h PRIVMSG #test :mira esto https://example.com/noticia")
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("mira esto", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        waitForIdle()
    }

    @Test
    fun conElAjusteApagado_noSePideNiSePintaNada() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)
        receiveLink()
        Thread.sleep(500)
        waitForIdle()

        assertEquals(0, fetcher.count(), "Con el ajuste apagado no debe visitarse ninguna web")
        onNodeWithText("Titular de prueba").assertDoesNotExist()
    }

    @Test
    fun conElAjusteActivado_pintaLaTarjetaDelEnlace() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        runBlocking { koin.get<UserPreferencesRepository>().updateShowLinkPreviews(true) }
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)
        receiveLink()

        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Titular de prueba").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Resumen de la web").assertExists()
        assertEquals(listOf("https://example.com/noticia"), synchronized(fetcher.calls) { fetcher.calls.toList() })
    }

    @Test
    fun unEnlaceALaRedLocal_nuncaSeVisita_aunqueElAjusteEsteActivado() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        runBlocking { koin.get<UserPreferencesRepository>().updateShowLinkPreviews(true) }
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)
        server.serverSays(":ana!u@h PRIVMSG #test :mi router http://192.168.1.1/admin y http://localhost:8080/x")
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("mi router", substring = true).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(500)
        waitForIdle()

        assertEquals(0, fetcher.count())
    }
}
