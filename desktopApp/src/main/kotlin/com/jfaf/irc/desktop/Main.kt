package com.jfaf.irc.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.platformLogWriter
import com.jfaf.irc.data.remote.IrcSession
import com.jfaf.irc.di.commonModule
import com.jfaf.irc.di.viewModelModule
import com.jfaf.irc.domain.platform.AppVisibility
import com.jfaf.irc.log.Log
import com.jfaf.irc.ui.App
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.startKoin
import javax.swing.JOptionPane
import kotlin.system.exitProcess

private const val APP_TITLE = "IRC"
private const val SHUTDOWN_TIMEOUT_MS = 3_000L

fun main() {
    val dataDir = DesktopPaths.defaultDataDir()

    // DataStore y Room son de un solo proceso: una segunda instancia no puede arrancar.
    val instance = SingleInstance.tryAcquire(dataDir)
    if (instance == null) {
        JOptionPane.showMessageDialog(
            null,
            "IRC ya está en ejecución.",
            APP_TITLE,
            JOptionPane.INFORMATION_MESSAGE
        )
        exitProcess(1)
    }

    Log.install(platformLogWriter())
    DesktopTls.install() // antes de abrir ninguna conexión TLS
    val koin = startKoin {
        modules(commonModule, viewModelModule, desktopModule(dataDir))
    }.koin

    val notifier = koin.get<DesktopNotifier>()
    val appVisibility = koin.get<AppVisibility>()
    val session = koin.get<IrcSession>()

    // Envía el QUIT antes de salir (hasta SHUTDOWN_TIMEOUT_MS) para no dejar la conexión colgada en el servidor.
    fun shutdown() {
        runBlocking { withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) { session.disconnect() } }
    }

    application {
        val exit = {
            shutdown()
            instance.close()
            exitApplication()
        }
        val windowState = rememberWindowState(size = DpSize(1000.dp, 700.dp))
        val appIcon = remember { BitmapPainter(useResource("icon.png", ::loadImageBitmap)) }

        // Los avisos de privados salen por la bandeja del sistema.
        val trayState = rememberTrayState()
        if (isTraySupported) {
            DisposableEffect(trayState) {
                notifier.attach(trayState)
                onDispose { notifier.attach(null) }
            }
            Tray(state = trayState, icon = appIcon, tooltip = APP_TITLE)
        }

        Window(onCloseRequest = exit, title = APP_TITLE, icon = appIcon, state = windowState) {
            // Visible = con el foco y sin minimizar; si no, BackgroundNotifications avisa por la bandeja.
            val focused = LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(focused, windowState.isMinimized) {
                if (focused && !windowState.isMinimized) {
                    appVisibility.enteredForeground()
                } else {
                    appVisibility.enteredBackground()
                }
            }

            App(
                platformUi = DesktopPlatformUi,
                onGoogleSignIn = {},
                onRedirectToPlayStore = {},
                onExit = exit
            )
        }
    }
}
