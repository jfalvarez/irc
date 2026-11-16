package com.jfaf.irc.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import com.jfaf.irc.domain.notifications.ChannelNotificationLevel
import com.jfaf.irc.ui.platform.LocalPlatformUi
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

/** UI-34: la sección «Notificaciones de los canales» de Ajustes cambia el nivel por defecto. */
@OptIn(ExperimentalTestApi::class)
class ChannelNotificationsSettingsUiTest {

    private lateinit var dataDir: File
    private lateinit var app: KoinApplication
    private lateinit var koin: Koin

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("irc-notif-settings-test").toFile()
        app = koinApplication { modules(commonModule, viewModelModule, desktopModule(dataDir)) }
        koin = app.koin
    }

    @AfterTest
    fun tearDown() {
        runCatching { koin.get<ChatDatabase>().close() }
        app.close()
        dataDir.deleteRecursively()
    }

    @Test
    fun elegirUnNivel_cambiaElPredeterminado() = runSkikoComposeUiTest(size = Size(900f, 3000f)) {
        val settings = koin.get<SettingsViewModel>()
        setContent {
            IRCAppTheme {
                CompositionLocalProvider(LocalPlatformUi provides DesktopPlatformUi()) {
                    SettingsScreen(onNavigateUp = {}, onOpenFriends = {}, onOpenIgnored = {}, viewModel = settings)
                }
            }
        }
        waitForIdle()
        onNodeWithText("Notificaciones de los canales").assertExists()
        assertEquals(ChannelNotificationLevel.MENTIONS, settings.defaultChannelNotificationLevel.value)

        onNode(hasText("Todos los mensajes")).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { settings.defaultChannelNotificationLevel.value == ChannelNotificationLevel.ALL }

        onNode(hasText("Silenciado")).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { settings.defaultChannelNotificationLevel.value == ChannelNotificationLevel.MUTED }
    }
}
