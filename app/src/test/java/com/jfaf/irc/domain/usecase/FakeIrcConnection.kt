package com.jfaf.irc.domain.usecase

import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.domain.IrcConnection
import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** [IrcConnection] en memoria: registra lo que se envía como texto legible. */
class FakeIrcConnection(connected: Boolean = true) : IrcConnection {
    override val connectionState = MutableStateFlow(connected)
    override val isReconnecting = MutableStateFlow(false)
    override val incomingMessages = MutableSharedFlow<ParsedIrcMessage>()

    val sent = mutableListOf<String>()

    override fun connect(serverHost: String, serverPort: Int, useSsl: Boolean, nickname: String) {
        sent += "connect $nickname@$serverHost:$serverPort ssl=$useSsl"
    }
    override fun disconnect(quitMessage: String?) { sent += "disconnect $quitMessage" }
    override fun joinChannel(channelName: String, key: String?) { sent += "join $channelName ${key.orEmpty()}".trim() }
    override fun partChannel(channelName: String, partMessage: String?) { sent += "part $channelName" }
    override fun sendMessage(target: String, message: String) { sent += "msg $target $message" }
    override fun sendRawCommand(command: String, isSilent: Boolean) { sent += "raw $command" }
    override fun isWhoisSilent(nick: String) = false
    override fun completeSilentWhois(nick: String) {}
}

/** Textos estables en los tests: "CLAVE|arg1|arg2". */
val fakeStrings = object : StringProvider {
    override fun get(msg: Msg, vararg args: Any) = (listOf(msg.name) + args.map { it.toString() }).joinToString("|")
}
