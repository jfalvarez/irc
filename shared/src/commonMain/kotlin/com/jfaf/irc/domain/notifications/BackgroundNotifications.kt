package com.jfaf.irc.domain.notifications

import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.domain.platform.AppVisibility
import com.jfaf.irc.domain.user.FriendPresenceTracker
import com.jfaf.irc.domain.user.UserMetadataStore
import com.jfaf.irc.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Decide qué avisar mientras la app no está a la vista y se lo pide al [Notifier]. */
class BackgroundNotifications(
    private val notifier: Notifier,
    private val appVisibility: AppVisibility,
    private val userMetadataStore: UserMetadataStore,
    private val friendPresence: FriendPresenceTracker,
    /** Nivel de aviso de cada canal (UI-32). Sin él, todos los canales tienen el predeterminado. */
    private val channelStore: ChannelNotificationStore = NoChannelNotificationLevels,
    /** El nick del usuario ahora mismo, para saber si lo nombran; null si no hay conexión. */
    private val ownNick: () -> String? = { null }
) {
    /** Empieza a vigilar [messages] y los amigos que se conectan; se detiene al cancelar el Job. */
    fun start(scope: CoroutineScope, messages: Flow<ParsedIrcMessage>): Job = scope.launch {
        launch {
            messages.collect { message ->
                // Comprobación barata primero: con la app a la vista no se avisa de nada
                if (appVisibility.isInForeground.value) return@collect
                if (ChannelNotificationPolicy.isChannelMessage(message)) {
                    notifyChannelMessage(message)
                    return@collect
                }
                if (!PmNotificationPolicy.isPrivateMessage(message)) return@collect
                val ignored = userMetadataStore.ignoredUsersFlow.first()
                if (PmNotificationPolicy.shouldNotify(message, ignored, appInForeground = false)) {
                    val key = PmNotificationPolicy.conversationKey(message)
                    val sender = message.senderNickname ?: message.prefix ?: "Server"
                    Log.d(TAG, "Privado de $sender con la app en segundo plano: notificando.")
                    notifier.showPrivateMessage(sender, message.trailing.orEmpty(), key)
                } else {
                    Log.d(TAG, "Privado de usuario ignorado (${message.senderNickname}): sin notificación.")
                }
            }
        }
        launch {
            friendPresence.friendCameOnlineEvent.collect { nick ->
                if (!appVisibility.isInForeground.value) notifier.showFriendOnline(nick)
            }
        }
    }

    /** Avisa de un mensaje de canal según el nivel de ese canal (UI-32): silenciado, sólo menciones o todo. */
    private suspend fun notifyChannelMessage(message: ParsedIrcMessage) {
        val channel = ChannelNotificationPolicy.channelOf(message) ?: return
        val level = ChannelNotificationLevels.levelFor(channelStore.levelsFlow.first(), channel)
        if (level == ChannelNotificationLevel.MUTED) return // barato: ni se miran los ignorados
        val ignored = userMetadataStore.ignoredUsersFlow.first()
        val alert = ChannelNotificationPolicy.decide(message, level, ownNick(), ignored) ?: return
        Log.d(TAG, "Mensaje de ${alert.sender} en ${alert.channel} con la app en segundo plano: notificando (mención: ${alert.isMention}).")
        notifier.showChannelMessage(alert.channel, alert.sender, alert.text, alert.isMention)
    }

    private companion object {
        const val TAG = "BackgroundNotifications"
    }
}
