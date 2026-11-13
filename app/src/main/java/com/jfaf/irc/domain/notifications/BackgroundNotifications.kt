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
import javax.inject.Inject

/** Decide qué avisar mientras la app no está a la vista y se lo pide al [Notifier]. */
class BackgroundNotifications @Inject constructor(
    private val notifier: Notifier,
    private val appVisibility: AppVisibility,
    private val userMetadataStore: UserMetadataStore,
    private val friendPresence: FriendPresenceTracker
) {
    /** Empieza a vigilar [messages] y los amigos que se conectan; se detiene al cancelar el Job. */
    fun start(scope: CoroutineScope, messages: Flow<ParsedIrcMessage>): Job = scope.launch {
        launch {
            messages.collect { message ->
                // Comprobación barata primero: los ignorados sólo se leen para privados en segundo plano
                if (appVisibility.isInForeground.value || !PmNotificationPolicy.isPrivateMessage(message)) return@collect
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

    private companion object {
        const val TAG = "BackgroundNotifications"
    }
}
