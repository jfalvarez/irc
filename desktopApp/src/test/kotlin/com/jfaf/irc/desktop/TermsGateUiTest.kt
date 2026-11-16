package com.jfaf.irc.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import com.jfaf.irc.domain.terms.TermsOfUse
import com.jfaf.irc.ui.App
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Condiciones de uso: la app no se puede usar hasta aceptarlas, y sólo se piden una vez. */
@OptIn(ExperimentalTestApi::class)
class TermsGateUiTest {

    private lateinit var dataDir: File
    private lateinit var koin: Koin
    private var exited = false

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-terms-test").toFile()
        koin = startKoin { modules(commonModule, viewModelModule, desktopModule(dataDir)) }.koin
    }

    @AfterTest
    fun tearDown() {
        runCatching { koin.get<ChatDatabase>().close() }
        stopKoin()
        dataDir.deleteRecursively()
    }

    private fun SkikoComposeUiTest.showApp() = setContent {
        App(platformUi = DesktopPlatformUi(), onRedirectToPlayStore = {}, onExit = { exited = true })
    }

    private fun SkikoComposeUiTest.isShown(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun acceptedVersion() = runBlocking { koin.get<UserPreferencesRepository>().termsAcceptedVersionFlow.first() }

    @Test
    fun primeraVez_pideLasCondicionesYAlAceptarSeGuardan() = runSkikoComposeUiTest {
        showApp()
        waitUntil(timeoutMillis = 5_000) { isShown("Acepto") }
        onNodeWithText("Acepto").performClick()
        waitUntil(timeoutMillis = 5_000) { !isShown("Acepto") }
        assertEquals(TermsOfUse.CURRENT_VERSION, acceptedVersion())
        assertTrue(!exited)
    }

    @Test
    fun alNoAceptar_seSaleDeLaApp() = runSkikoComposeUiTest {
        showApp()
        waitUntil(timeoutMillis = 5_000) { isShown("Salir") }
        onNodeWithText("Salir").performClick()
        waitUntil(timeoutMillis = 5_000) { exited }
        assertEquals(null, acceptedVersion())
    }

    @Test
    fun yaAceptadas_noSeVuelvenAPedir() = runSkikoComposeUiTest {
        runBlocking { koin.get<UserPreferencesRepository>().acceptTerms(TermsOfUse.CURRENT_VERSION) }
        showApp()
        waitForIdle()
        assertTrue(!isShown("Acepto"), "No debe salir el diálogo de las condiciones")
    }
}
