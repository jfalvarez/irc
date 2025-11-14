package com.jfaf.irc.service

import android.util.Log
import com.jfaf.irc.data.model.ParsedIrcMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object IrcServiceApi {
    private const val TAG = "IrcServiceApi"

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<ParsedIrcMessage>(replay = 10, extraBufferCapacity = 10)
    val incomingMessages: SharedFlow<ParsedIrcMessage> = _incomingMessages.asSharedFlow()

    private val _connectionError = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val connectionError: SharedFlow<String> = _connectionError.asSharedFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    fun updateConnectionState(isConnected: Boolean) {
        _connectionState.value = isConnected
        Log.d(TAG, "Connection state updated to: $isConnected")
    }

    suspend fun postMessage(message: ParsedIrcMessage) {
        _incomingMessages.emit(message)
    }

    suspend fun postConnectionError(error: String) {
        _connectionError.emit(error)
    }

    fun setAppInForeground(isInForeground: Boolean) {
        _isAppInForeground.value = isInForeground
        Log.d(TAG, "App foreground state updated to: $isInForeground")
    }
}
