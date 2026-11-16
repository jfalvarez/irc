package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.IrcConnection
import com.jfaf.irc.domain.channel.ChannelListCollector
import com.jfaf.irc.log.Log

sealed interface ChannelListRequestResult {
    object Success : ChannelListRequestResult
    object NotConnected : ChannelListRequestResult
}

/** Pide al servidor la lista de canales (`LIST`) y anuncia la consulta al [ChannelListCollector]. */
class RequestChannelListUseCase(
    private val ircConnection: IrcConnection,
    private val channelListCollector: ChannelListCollector
) {
    operator fun invoke(): ChannelListRequestResult {
        if (!ircConnection.connectionState.value) {
            Log.w("RequestChannelListUC", "LIST pedido sin conexión.")
            return ChannelListRequestResult.NotConnected
        }
        channelListCollector.expect()
        ircConnection.sendRawCommand("LIST", isSilent = false)
        return ChannelListRequestResult.Success
    }
}
