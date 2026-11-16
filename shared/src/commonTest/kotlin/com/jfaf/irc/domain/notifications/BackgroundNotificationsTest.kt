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
        override fun showChannelMessage(channel: String, sender: String, text: String, isMention: Boolean) {
            calls += "channel $channel $sender '$text' mention=$isMention"
        }
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

    private var levels: Map<String, ChannelNotificationLevel> = emptyMap()
    private var defaultLevel = ChannelNotificationLevel.Default
    private val channelStore = object : ChannelNotificationStore {
        override val levelsFlow: Flow<Map<String, ChannelNotificationLevel>> get() = flowOf(levels)
        override val defaultLevelFlow: Flow<ChannelNotificationLevel> get() = flowOf(defaultLevel)
        override suspend fun setLevel(channel: String, level: ChannelNotificationLevel?) {}
        override suspend fun setDefaultLevel(level: ChannelNotificationLevel) {}
    }

    private fun start(ownNick: String? = null) {
        BackgroundNotifications(notifier, visibility, store, friends, channelStore, ownNick = { ownNick }).start(scope, messages)
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

    // --- Canales (UI-32) ---
    @Test
    fun mencionEnUnCanal_enSegundoPlano_notificaPorDefecto() = runBlocking {
        visibility.enteredBackground()
        start(ownNick = "Yo")
        send(":Ana!u@h PRIVMSG #chat :hola Yo, mira")
        withTimeout(2_000) { while (calls.isEmpty()) delay(5) }
        assertEquals(listOf("channel #chat Ana 'hola Yo, mira' mention=true"), calls.toList())
    }

    @Test
    fun mensajeSinMencion_conElNivelPorDefecto_noNotifica() = runBlocking {
        visibility.enteredBackground()
        start(ownNick = "Yo")
        send(":Ana!u@h PRIVMSG #chat :hola a todos")
        settle()
        assertEquals(emptyList<String>(), calls.toList())
    }

    @Test
    fun canalEnTodo_notificaCadaMensaje() = runBlocking {
        levels = mapOf("#chat" to ChannelNotificationLevel.ALL)
        visibility.enteredBackground()
        start(ownNick = "Yo")
        send(":Ana!u@h PRIVMSG #CHAT :hola a todos")
        withTimeout(2_000) { while (calls.isEmpty()) delay(5) }
        assertEquals(listOf("channel #CHAT Ana 'hola a todos' mention=false"), calls.toList())
    }

    @Test
    fun canalSilenciado_noNotificaNiMenciones() = runBlocking {
        levels = mapOf("#chat" to ChannelNotificationLevel.MUTED)
        visibility.enteredBackground()
        start(ownNick = "Yo")
        send(":Ana!u@h PRIVMSG #chat :Yo, te hablo")
        settle()
        assertEquals(emptyList<String>(), calls.toList())
    }

    @Test
    fun elNivelEsPorCanal() = runBlocking {
        levels = mapOf("#ruido" to ChannelNotificationLevel.MUTED)
        visibility.enteredBackground()
        start(ownNick = "Yo")
        send(":Ana!u@h PRIVMSG #ruido :Yo, te hablo")
        send(":Ana!u@h PRIVMSG #otro :Yo, te hablo")
        withTimeout(2_000) { while (calls.isEmpty()) delay(5) }
        settle()
        assertEquals(listOf("channel #otro Ana 'Yo, te hablo' mention=true"), calls.toList())
    }

    @Test
    fun elNivelPredeterminadoDeAjustes_valeParaLosCanalesSinNivelPropio() = runBlocking {
        defaultLevel = ChannelNotificationLevel.ALL
        levels = mapOf("#ruido" to ChannelNotificationLevel.MUTED)
        visibility.enteredBackground()
        start(ownNick = "Yo")
        send(":Ana!u@h PRIVMSG #ruido :hola") // propio: silenciado
        send(":Ana!u@h PRIVMSG #otro :hola a todos") // sin propio: sigue el predeterminado (todo)
        withTimeout(2_000) { while (calls.isEmpty()) delay(5) }
        settle()
        assertEquals(listOf("channel #otro Ana 'hola a todos' mention=false"), calls.toList())
    }

    @Test
    fun conElPredeterminadoSilenciado_unCanalConNivelPropioSigueAvisando() = runBlocking {
        defaultLevel = ChannelNotificationLevel.MUTED
        levels = mapOf("#chat" to ChannelNotificationLevel.MENTIONS)
        visibility.enteredBackground()
        start(ownNick = "Yo")
        send(":Ana!u@h PRIVMSG #otro :Yo, te hablo") // sin propio: silenciado
        send(":Ana!u@h PRIVMSG #chat :Yo, te hablo")
        withTimeout(2_000) { while (calls.isEmpty()) delay(5) }
        settle()
        assertEquals(listOf("channel #chat Ana 'Yo, te hablo' mention=true"), calls.toList())
    }

    @Test
    fun mencionConLaAppVisible_noNotifica() = runBlocking {
        start(ownNick = "Yo")
        send(":Ana!u@h PRIVMSG #chat :Yo, te hablo")
        settle()
        assertEquals(emptyList<String>(), calls.toList())
    }

    @Test
    fun mencionDeUnIgnorado_noNotifica() = runBlocking {
        visibility.enteredBackground()
        start(ownNick = "Yo")
        send(":pesado!u@h PRIVMSG #chat :Yo, te hablo")
        settle()
        assertEquals(emptyList<String>(), calls.toList())
    }
}
