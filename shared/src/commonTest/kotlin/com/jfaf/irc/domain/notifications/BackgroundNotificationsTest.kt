package com.jfaf.irc.domain.notifications

import com.jfaf.irc.data.model.IrcLineParser
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.domain.platform.AppVisibility
import com.jfaf.irc.domain.user.FriendPresenceTracker
import com.jfaf.irc.domain.user.UserMetadataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.Test
import java.util.Collections

class BackgroundNotificationsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val calls = Collections.synchronizedList(mutableListOf<String>())
    private val notifier = object : Notifier {
        override fun showPrivateMessage(sender: String, text: String, conversationKey: String) {
            calls += "pm $sender '$text' key=$conversationKey"
        }
        override fun showFriendOnline(nick: String) { calls += "friend $nick" }
    }
    private val visibility = AppVisibility()
    private val friends = FriendPresenceTracker { 0L }
    private val store = object : UserMetadataStore {
        override val ignoredUsersFlow: Flow<Set<String>> = flowOf(setOf("pesado"))
        override val friendsFlow: Flow<Set<String>> = flowOf(emptySet())
        override suspend fun addIgnoredUser(nick: String) {}
        override suspend fun removeIgnoredUser(nick: String) {}
        override suspend fun addFriend(nick: String) {}
        override suspend fun removeFriend(nick: String) {}
    }
    private val messages = MutableSharedFlow<ParsedIrcMessage>(extraBufferCapacity = 16)

    private fun start() {
        BackgroundNotifications(notifier, visibility, store, friends).start(scope, messages)
    }

    private suspend fun send(line: String) {
        withTimeout(2_000) { while (messages.subscriptionCount.value == 0) delay(5) }
        messages.emit(IrcLineParser.parse(line)!!)
    }

    /** Espera a que se procese lo enviado (no hay forma de saber cuándo "no pasa nada"). */
    private suspend fun settle() = delay(150)

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun privadoEnSegundoPlano_notificaPorConversacion() = runBlocking {
        visibility.enteredBackground()
        start()
        send(":Ana!u@h PRIVMSG yo :hola")
        withTimeout(2_000) { while (calls.isEmpty()) delay(5) }
        assertEquals(listOf("pm Ana 'hola' key=ana"), calls.toList())
    }

    @Test
    fun privadoConAppVisible_noNotifica() = runBlocking {
        start()
        send(":Ana!u@h PRIVMSG yo :hola")
        settle()
        assertEquals(emptyList<String>(), calls.toList())
    }

    @Test
    fun privadoDeIgnorado_noNotifica() = runBlocking {
        visibility.enteredBackground()
        start()
        send(":PESADO!u@h PRIVMSG yo :otra vez")
        settle()
        assertEquals(emptyList<String>(), calls.toList())
    }

    @Test
    fun mensajeDeCanal_noNotifica() = runBlocking {
        visibility.enteredBackground()
        start()
        send(":Ana!u@h PRIVMSG #chat :hola a todos")
        settle()
        assertEquals(emptyList<String>(), calls.toList())
    }

    @Test
    fun amigoConectado_soloEnSegundoPlano() = runBlocking {
        start()
        settle() // deja que el coordinador se suscriba a los avisos de amigos
        friends.friendSeen("visible")      // app a la vista: nada
        settle()
        visibility.enteredBackground()
        friends.friendSeen("luis")
        withTimeout(2_000) { while (calls.isEmpty()) delay(5) }
        assertEquals(listOf("friend luis"), calls.toList())
    }
}
