package com.jfaf.irc.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import com.jfaf.irc.domain.platform.Haptics
import com.jfaf.irc.ui.platform.LocalPlatformUi
import com.jfaf.irc.ui.platform.NoPlatformUi
import com.jfaf.irc.ui.platform.PlatformUi
import com.jfaf.irc.ui.screens.settings.SettingsScreen
import com.jfaf.irc.ui.theme.IRCAppTheme
import com.jfaf.irc.ui.viewmodels.SettingsViewModel
import com.jfaf.irc.domain.notifications.Notifier
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El interruptor de sonido de punta a punta: la casilla de Ajustes (sólo en escritorio) guarda la preferencia y el
 * sonido de la app (con la ventana a la vista o en segundo plano) deja de sonar.
 */
@OptIn(ExperimentalTestApi::class)
class SoundSettingTest {

    private class CountingPlayer : SoundPlayer {
        @Volatile
        var plays = 0
        override fun play() {
            plays++
        }
    }

    private lateinit var dataDir: File
    private lateinit var app: KoinApplication
    private lateinit var koin: Koin
    private val output = CountingPlayer()

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-sound-setting-test").toFile()
        app = koinApplication { modules(commonModule, viewModelModule, desktopModule(dataDir, output)) }
        koin = app.koin
    }

    @AfterTest
    fun tearDown() {
        runCatching { koin.get<ChatDatabase>().close() }
        app.close()
        dataDir.deleteRecursively()
    }

    /** El valor llega al interruptor por un flujo del DataStore: se espera a que lo vea en vez de dormir a ciegas. */
    private fun awaitSetting(expected: Boolean) {
        val setting = koin.get<SoundAlertsSetting>()
        val end = System.currentTimeMillis() + 5_000
        while (setting.enabled.value != expected && System.currentTimeMillis() < end) Thread.sleep(20)
        assertEquals(expected, setting.enabled.value, "El interruptor no llegó a ${if (expected) "activarse" else "silenciarse"}")
    }

    @Test
    fun porDefecto_suenaConLaVentanaALaVista() {
        koin.get<Haptics>().vibrate()
        assertEquals(1, output.plays)
    }

    @Test
    fun porDefecto_suenaEnSegundoPlano() {
        koin.get<Notifier>().showPrivateMessage("Ana", "hola", "Ana")
        assertEquals(1, output.plays)
    }

    @Test
    fun silenciadoDesdeAjustes_noSuenaConLaVentanaALaVista() {
        koin.get<SettingsViewModel>().setSoundAlerts(false)
        awaitSetting(false)

        koin.get<Haptics>().vibrate()

        assertEquals(0, output.plays)
    }

    @Test
    fun silenciadoDesdeAjustes_noSuenaEnSegundoPlano() {
        koin.get<SettingsViewModel>().setSoundAlerts(false)
        awaitSetting(false)

        koin.get<Notifier>().showPrivateMessage("Ana", "hola", "Ana")
        koin.get<Notifier>().showFriendOnline("Luis")

        assertEquals(0, output.plays)
    }

    @Test
    fun alVolverAActivarlo_vuelveASonar() {
        koin.get<SettingsViewModel>().setSoundAlerts(false)
        awaitSetting(false)
        koin.get<SettingsViewModel>().setSoundAlerts(true)
        awaitSetting(true)

        koin.get<Haptics>().vibrate()

        assertEquals(1, output.plays)
    }

    // --- La casilla de Ajustes
    private fun SkikoComposeUiTest.count(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun laCasillaSoloSeMuestraEnEscritorioYGuardaLaPreferencia() = runSkikoComposeUiTest(size = Size(900f, 1800f)) {
        val settings = koin.get<SettingsViewModel>()
        var platform: PlatformUi by mutableStateOf(DesktopPlatformUi)
        setContent {
            IRCAppTheme {
                CompositionLocalProvider(LocalPlatformUi provides platform) {
                    SettingsScreen(onNavigateUp = {}, viewModel = settings)
                }
            }
        }
        waitForIdle()
        assertEquals(1, count("Sonido al recibir un privado"), "En escritorio debe verse la casilla")

        onNodeWithText("Sonido al recibir un privado", substring = true).performClick()
        awaitSetting(false)

        platform = NoPlatformUi // como en Android: el sonido no es de la app
        waitForIdle()
        assertEquals(0, count("Sonido al recibir un privado"), "Fuera de escritorio no debe verse")
    }
}
