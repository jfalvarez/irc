package com.jfaf.irc.data.remote

import com.jfaf.irc.AppConstants
import com.jfaf.irc.data.model.ParsedIrcMessage
import com.jfaf.irc.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** Estado de la sesión para la notificación persistente (o lo que muestre cada plataforma). */
sealed interface SessionStatus {
    data object Disconnected : SessionStatus
    data object Connecting : SessionStatus
    data class Connected(val host: String) : SessionStatus
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : SessionStatus
}

data class ConnectParams(val nickname: String, val host: String, val port: Int, val useSsl: Boolean)

/** Backoff exponencial: 2 s, 4 s, 8 s… hasta 60 s; 8 intentos; 15 s para recibir el 001. */
data class ReconnectPolicy(
    val baseDelayMs: Long = 2_000L,
    val maxDelayMs: Long = 60_000L,
    val maxAttempts: Int = 8,
    val registerTimeoutMs: Long = 15_000L
) {
    fun delayFor(attempt: Int): Long = min(baseDelayMs shl (attempt - 1), maxDelayMs)
}

/**
 * Sesión IRC del proceso: crea el cliente, reconecta tras una pérdida y publica estado y mensajes.
 * No sabe nada de Android; el servicio (o la app de escritorio) sólo la mantiene viva y la muestra.
 */
@Singleton
class IrcSession(
    private val scope: CoroutineScope,
    private val policy: ReconnectPolicy,
    private val clientFactory: (ConnectParams, CoroutineScope, onConnectionLost: () -> Unit) -> ManualIrcClient
) {
    @Inject constructor() : this(
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ReconnectPolicy(),
        { p, s, onLost -> ManualIrcClient(p.host, p.port, p.useSsl, s, Dispatchers.IO, onLost) }
    )

    private companion object {
        const val TAG = "IrcSession"
        const val IRC_USER = "ircandroid"
        const val IRC_REAL_NAME = "Android IRC User"
    }

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    private val _status = MutableStateFlow<SessionStatus>(SessionStatus.Disconnected)
    val status: StateFlow<SessionStatus> = _status.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<ParsedIrcMessage>(extraBufferCapacity = 256)
    val incomingMessages: SharedFlow<ParsedIrcMessage> = _incomingMessages.asSharedFlow()

    @Volatile private var client: ManualIrcClient? = null
    private var lastParams: ConnectParams? = null
    @Volatile private var reconnectJob: Job? = null
    private var collectorsJob: Job? = null
    private var reconnectAttempt = 0

    @Synchronized
    fun connect(params: ConnectParams) {
        cancelReconnect()
        lastParams = params
        val current = client
        if (current != null && current.isConnected) {
            Log.w(TAG, "Ya conectado o conectando.")
            _connectionState.value = true
            _status.value = SessionStatus.Connected(current.host)
            return
        }
        Log.i(TAG, "Conectando a ${params.host}:${params.port} como ${params.nickname} (SSL: ${params.useSsl})")
        // Se captura el cliente anterior: antes se leía `manualIrcClient` dentro del launch y,
        // si la corrutina corría tarde, se desconectaba el cliente NUEVO.
        current?.let { old -> scope.launch { old.disconnectAndCleanup() } }
        _status.value = SessionStatus.Connecting

        val newClient = clientFactory(params, scope) { scheduleReconnect() }
        client = newClient
        observe(newClient)
        newClient.connect(params.nickname, user = IRC_USER, real = IRC_REAL_NAME)
    }

    /** Envía QUIT (esperando a que salga), cierra y deja la sesión sin cliente. */
    suspend fun disconnect(quitMessage: String = AppConstants.QUIT_MSG_CLIENT_SHUTDOWN) {
        cancelReconnect()
        val c = synchronized(this) {
            val current = client
            client = null
            collectorsJob?.cancel()
            collectorsJob = null
            current
        }
        c?.disconnectAndCleanup(quitMessage)
        _connectionState.value = false
        _status.value = SessionStatus.Disconnected
    }

    /** Para quien no puede esperar (p. ej. `onDestroy`): desconecta en el scope de la sesión. */
    fun disconnectInBackground(quitMessage: String = AppConstants.QUIT_MSG_CLIENT_SHUTDOWN) {
        scope.launch { disconnect(quitMessage) }
    }

    fun sendMessage(target: String, message: String) { client?.sendMessageToChannel(target, message) }
    fun joinChannel(channel: String, key: String?) { client?.joinChannel(channel, key) }
    fun partChannel(channel: String, partMessage: String?) { client?.partFromChannel(channel, partMessage) }
    fun sendRaw(command: String) { client?.sendRaw(command) }

    private fun observe(c: ManualIrcClient) {
        collectorsJob?.cancel() // que no se acumulen colectores de clientes anteriores
        collectorsJob = scope.launch {
            launch {
                c.connectionState.collect { connected ->
                    // Durante una reconexión el socket abierto aún no es "conectado": se espera al 001
                    // (lo publica el bucle de reconexión). Evita el parpadeo Reconectado/Conexión perdida.
                    if (connected && reconnectJob?.isActive == true && !c.isRegistered.value) return@collect
                    _connectionState.value = connected
                    if (connected) _status.value = SessionStatus.Connected(c.host)
                    else if (reconnectJob?.isActive != true) _status.value = SessionStatus.Disconnected
                }
            }
            launch { c.incomingMessages.collect { _incomingMessages.emit(it) } }
        }
    }

    @Synchronized
    private fun scheduleReconnect() {
        val params = lastParams ?: return
        if (reconnectJob?.isActive == true) return
        _isReconnecting.value = true
        reconnectJob = scope.launch {
            try {
                while (reconnectAttempt < policy.maxAttempts) {
                    reconnectAttempt++
                    _status.value = SessionStatus.Reconnecting(reconnectAttempt, policy.maxAttempts)
                    val delayMs = policy.delayFor(reconnectAttempt)
                    Log.i(TAG, "Reconexión $reconnectAttempt/${policy.maxAttempts} en ${delayMs}ms")
                    delay(delayMs)
                    val c = client ?: return@launch
                    c.connect(params.nickname, user = IRC_USER, real = IRC_REAL_NAME)
                    val registered = withTimeoutOrNull(policy.registerTimeoutMs) { c.isRegistered.first { it } } != null
                    if (registered) {
                        reconnectAttempt = 0
                        _connectionState.value = true
                        _status.value = SessionStatus.Connected(c.host)
                        return@launch
                    }
                    c.disconnectAndCleanup()
                }
                Log.w(TAG, "Reconexión abandonada tras ${policy.maxAttempts} intentos")
                _status.value = SessionStatus.Disconnected
            } finally {
                _isReconnecting.value = false // éxito, abandono o cancelación
            }
        }
    }

    @Synchronized
    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
    }
}
