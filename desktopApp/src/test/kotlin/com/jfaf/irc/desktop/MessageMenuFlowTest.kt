package com.jfaf.irc.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** UI-27: el menú de un mensaje (mantener pulsado) de punta a punta: mencionar, citar y copiar. */
@OptIn(ExperimentalTestApi::class)
class MessageMenuFlowTest {

    private lateinit var dataDir: File
    private lateinit var koin: Koin
    private lateinit var server: ScriptedTransport

    /** Anota las vibraciones que pide la interfaz. */
    private class RecordingHaptics : HapticFeedback {
        val calls = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            calls += hapticFeedbackType
        }
    }

    private val haptics = RecordingHaptics()

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-msgmenu-test").toFile()
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
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                App(platformUi = DesktopPlatformUi(), onRedirectToPlayStore = {}, onExit = {}, mainViewModel = vm)
            }
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

    private fun SkikoComposeUiTest.longPressMessage(vm: MainViewModel) {
        server.serverSays(":ana!u@h PRIVMSG #test :hola a todos")
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("hola a todos", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasAnyDescendant(hasText("hola a todos", substring = true)) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            .performSemanticsAction(SemanticsActions.OnLongClick)
        waitForIdle()
    }

    private fun SkikoComposeUiTest.inputText(): String =
        onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun laPulsacionLarga_dejaUnToqueHaptico_yUnToqueNormalNo() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)
        server.serverSays(":ana!u@h PRIVMSG #test :hola a todos")
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("hola a todos", substring = true).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        assertEquals(emptyList(), haptics.calls, "Sin pulsar nada no vibra")

        onNodeWithText("hola a todos", substring = true).performClick()
        waitForIdle()
        assertEquals(emptyList(), haptics.calls, "Un toque normal no vibra")

        longPressMessage(vm)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.calls)
        onNodeWithText("Mencionar").assertExists()
    }

    @Test
    fun mencionarPoneElNickEnElCampo() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)
        longPressMessage(vm)

        onNodeWithText("Mencionar").performClick()
        waitForIdle()

        assertEquals("ana: ", inputText())
    }

    @Test
    fun citarPoneElTextoEntreComillas() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)
        longPressMessage(vm)

        onNodeWithText("Citar").performClick()
        waitForIdle()

        assertEquals("ana: «hola a todos» ", inputText())
    }

    @Test
    fun copiarAvisaYNoTocaElCampo() = runSkikoComposeUiTest(size = Size(600f, 760f)) {
        val vm = koin.get<MainViewModel>()
        connectAndReceiveNames(vm)
        longPressMessage(vm)

        onNodeWithText("Copiar mensaje").assertExists()
        onNodeWithText("Copiar mensaje").performClick()
        waitForIdle()

        assertEquals("", inputText())
    }
}
