package com.jfaf.irc.desktop

import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import com.jfaf.irc.domain.notifications.Notifier
import com.jfaf.irc.log.Log
import kotlin.concurrent.Volatile

/**
 * Avisos por la bandeja del sistema. Compose Desktop sólo notifica desde un [TrayState] que vive
 * dentro de `application { }`, así que `main` lo registra con [attach] cuando existe.
 *
 * Además suena [sound]: así el aviso en segundo plano se oye aunque Windows tenga mudas las notificaciones o no
 * haya bandeja. Sólo se llama a este notificador con la ventana sin foco; con la ventana a la vista suena `Haptics`.
 */
class DesktopNotifier(private val sound: SoundPlayer) : Notifier {

    @Volatile
    private var trayState: TrayState? = null

    fun attach(state: TrayState?) {
        trayState = state
    }

    override fun showPrivateMessage(sender: String, text: String, conversationKey: String) {
        send(Notification(title = sender, message = text, type = Notification.Type.Info))
    }

    override fun showFriendOnline(nick: String) {
        send(Notification(title = nick, message = nick, type = Notification.Type.Info))
    }

    private fun send(notification: Notification) {
        sound.play()
        val state = trayState
        if (state == null) {
            Log.d("DesktopNotifier", "Sin bandeja disponible: no se muestra \"${notification.title}\"")
            return
        }
        state.sendNotification(notification)
    }
}
