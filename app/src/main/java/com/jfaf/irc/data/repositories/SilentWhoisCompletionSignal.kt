package com.jfaf.irc.data.repositories

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

class SilentWhoisCompletionSignal {
    private val channel = Channel<Unit>(1)

    suspend fun awaitCompletion(timeout: Long = 5000L) {
        withTimeoutOrNull(timeout) {
            channel.receive()
        }
    }

    fun signalCompletion() {
        channel.trySend(Unit)
    }
}
