package com.jfaf.irc.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import com.jfaf.irc.ui.platform.LocalPlatformUi
import com.jfaf.irc.ui.platform.NoPlatformUi
import com.jfaf.irc.ui.platform.PlatformUi
import com.jfaf.irc.ui.screens.settings.SettingsScreen
import com.jfaf.irc.ui.theme.IRCAppTheme
import com.jfaf.irc.ui.viewmodels.SettingsViewModel
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** UI-30: la sección «Atajos de teclado» de Ajustes sólo se muestra en escritorio y lista los atajos. */
@OptIn(ExperimentalTestApi::class)
class ShortcutsSettingsUiTest {

    private lateinit var dataDir: File
    private lateinit var app: KoinApplication
    private lateinit var koin: Koin

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-shortcuts-settings-test").toFile()
        app = koinApplication { modules(commonModule, viewModelModule, desktopModule(dataDir)) }
        koin = app.koin
    }

    @AfterTest
    fun tearDown() {
        runCatching { koin.get<ChatDatabase>().close() }
        app.close()
        dataDir.deleteRecursively()
    }

    private fun SkikoComposeUiTest.count(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun laSeccionSoloSeMuestraEnEscritorio() = runSkikoComposeUiTest(size = Size(900f, 2400f)) {
        val settings = koin.get<SettingsViewModel>()
        var platform: PlatformUi by mutableStateOf(DesktopPlatformUi())
        setContent {
            IRCAppTheme {
                CompositionLocalProvider(LocalPlatformUi provides platform) {
                    SettingsScreen(onNavigateUp = {}, onOpenFriends = {}, onOpenIgnored = {}, viewModel = settings)
                }
            }
        }
        waitForIdle()
        assertEquals(1, count("Atajos de teclado"), "En escritorio debe verse la sección")
        assertEquals(1, count("Ctrl+K"))
        assertEquals(1, count("Ctrl+Mayús+Tab"))
        assertEquals(1, count("Buscar un canal o conversación"))

        platform = NoPlatformUi // móvil: sin teclado de serie
        waitForIdle()
        assertEquals(0, count("Atajos de teclado"), "En el móvil no debe verse")
        assertEquals(0, count("Ctrl+K"))
    }
}
