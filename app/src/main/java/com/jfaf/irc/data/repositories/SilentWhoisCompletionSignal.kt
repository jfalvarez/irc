package com.jfaf.irc.data.repositories

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SilentWhoisCompletionSignal @Inject constructor() {
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
