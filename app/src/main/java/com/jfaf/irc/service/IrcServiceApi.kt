package com.jfaf.irc.service

import com.jfaf.irc.data.model.ParsedIrcMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton object to expose reactive streams from IrcService.
 * The ViewModel will observe these flows.
 */
object IrcServiceApi {
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    // Using a larger buffer for messages that might arrive while UI is not actively collecting
    private val _incomingMessages = MutableSharedFlow<ParsedIrcMessage>(
        replay = 50, // Keep last 50 messages for new collectors
        extraBufferCapacity = 50, // Additional buffer to prevent suspension
        onBufferOverflow = BufferOverflow.DROP_OLDEST // Drop oldest if buffer is full
    )
    val incomingMessages: SharedFlow<ParsedIrcMessage> = _incomingMessages.asSharedFlow()

    // --- Internal methods to be called by IrcService only ---
    internal fun updateConnectionState(isConnected: Boolean) {
        _connectionState.value = isConnected
    }

    internal suspend fun postMessage(message: ParsedIrcMessage) {
        _incomingMessages.emit(message)
    }
}