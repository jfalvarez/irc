package com.jfaf.irc.data.remote

import com.jfaf.irc.AppConstants
import com.jfaf.irc.log.Log
import com.jfaf.irc.data.model.IrcLineParser
import com.jfaf.irc.data.model.NickFallback
import com.jfaf.irc.data.model.ParsedIrcMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class ManualIrcClient(
    val host: String,
    private val port: Int,
    private val useSsl: Boolean,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Se invoca cuando la conexión se cierra sin que el usuario lo haya pedido. */
    private val onConnectionLost: () -> Unit = {},
    private val transport: IrcTransport = JvmSocketTransport()
) {
    private val TAG = "ManualIrcClient"

    private companion object {
        /** Tiempo máximo esperando a que salga el QUIT antes de cerrar la conexión. */
        const val QUIT_TIMEOUT_MS = 2_000L
    }

    // Buffer para que un colector lento no bloquee el bucle de lectura (ni retrase los PONG).
    private val _incomingMessages = MutableSharedFlow<ParsedIrcMessage>(extraBufferCapacity = 256)
    val incomingMessages: SharedFlow<ParsedIrcMessage> = _incomingMessages.asSharedFlow()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private var currentNickname: String = AppConstants.DEFAULT_BOT_NICKNAME
    private var desiredNickname: String = currentNickname
    private var nickFallbackAttempts = 0

    @Volatile private var closedByUser = false
    // canal (en minúsculas) -> clave ("" si no tiene). Sobrevive a las reconexiones.
    private val channelsToRejoin = java.util.concurrent.ConcurrentHashMap<String, String>()

    val isConnected: Boolean
        get() = _connectionState.value && transport.isOpen

    private val isActive: Boolean
        get() = coroutineScope.isActive

    fun connect(nick: String, user: String = nick, real: String = nick) {
        if (_connectionState.value) {
            Log.w(TAG, "Ya está conectado o conectando.")
            return
        }
        this.currentNickname = nick
        this.desiredNickname = nick
        nickFallbackAttempts = 0
        closedByUser = false

        coroutineScope.launch(ioDispatcher) {
            _isRegistered.value = false
            try {
                Log.d(TAG, "Intentando conectar a $host:$port (SSL: $useSsl), Nick: $currentNickname")
                transport.open(host, port, useSsl)

                if (transport.isOpen) {
                    _connectionState.value = true
                    Log.i(TAG, "Conectado a $host:$port.")

                    sendRawInternal("NICK $currentNickname")
                    sendRawInternal("USER $user 0 * :$real")

                    listenForMessages()
                } else {
                    throw Exception(AppConstants.ERROR_MSG_SOCKET_CONNECT_FAILED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error de conexión: ${e.message}", e)
                cleanupConnection()
                notifyConnectionLost()
            }
        }
    }

    private suspend fun listenForMessages() {
        try {
            while (isActive && _connectionState.value) {
                if (!transport.isOpen) break
                val line = withContext(ioDispatcher) { transport.readLine() }
                if (line != null) {
                    Log.d(TAG, "<<< $line")
                    val parsedMessage = IrcLineParser.parse(line)
                    if (parsedMessage == null) {
                        Log.w(TAG, "Línea IRC inválida descartada: $line")
                        continue
                    }
                    _incomingMessages.emit(parsedMessage)
                    handleInternalMessages(parsedMessage)
                } else {
                    Log.w(TAG, "readLine devolvió null. Conexión cerrada por el servidor.")
                    cleanupConnection()
                    notifyConnectionLost()
                    break
                }
            }
        } catch (e: Exception) {
            when {
                // Cierre pedido por nosotros: cerrar el socket es lo que desbloquea readLine(), no es un error.
                closedByUser -> Log.d(TAG, "Lectura interrumpida por el cierre local de la conexión.")
                isActive -> Log.e(TAG, "Error leyendo del socket: ${e.message}", e)
            }
            cleanupConnection()
            notifyConnectionLost()
        } finally {
            Log.d(TAG, "Bucle de escucha finalizado.")
        }
    }

    private fun handleInternalMessages(message: ParsedIrcMessage) {
        if (message.command == "PING") {
            val cookie = message.trailing ?: message.params.firstOrNull()
            if (cookie != null) {
                sendRaw("PONG :$cookie")
                Log.i(TAG, "PING? PONG! ($cookie)")
            }
        }
        if (message.command == "433" && !_isRegistered.value) { // ERR_NICKNAMEINUSE durante el registro
            val alternative = NickFallback.alternative(desiredNickname, ++nickFallbackAttempts)
            if (alternative != null) {
                Log.w(TAG, "Nick '$currentNickname' en uso; probando '$alternative'")
                currentNickname = alternative
                sendRaw("NICK $alternative")
            } else {
                Log.e(TAG, "Sin nicks alternativos para '$desiredNickname'.")
            }
        }
        if (message.command == "001") { // RPL_WELCOME
            _isRegistered.value = true
            val confirmedNick = message.params.firstOrNull() ?: currentNickname
            Log.i(TAG, "Cliente registrado con el servidor (RPL_WELCOME recibido). Nick confirmado: $confirmedNick")
            channelsToRejoin.forEach { (channel, key) ->
                sendRaw("JOIN $channel${if (key.isEmpty()) "" else " $key"}")
            }
        }
    }

    fun sendRaw(message: String) {
        if (!_connectionState.value || !transport.isOpen) {
            Log.w(TAG, "No conectado, no se puede enviar: $message")
            return
        }
        coroutineScope.launch(ioDispatcher) {
             sendRawInternal(message)
        }
    }

    private suspend fun sendRawInternal(message: String) {
        if (!_connectionState.value || !transport.isOpen) {
            Log.w(TAG, "No conectado, no se puede enviar (internal): $message")
            return
        }
        try {
            withContext(ioDispatcher) { transport.writeLine(message) }
            Log.d(TAG, ">>> $message")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando mensaje: ${e.message}", e)
            cleanupConnection()
            notifyConnectionLost()
        }
    }

    fun joinChannel(channelName: String, key: String? = null) {
        // La intención se guarda siempre: si aún no hay registro, se enviará al llegar el 001
        channelsToRejoin[channelName.lowercase()] = key.orEmpty()
        if (_isRegistered.value) {
            sendRaw("JOIN $channelName${key?.let { " $it" } ?: ""}")
        } else {
            Log.i(TAG, "Aún no registrado. JOIN a '$channelName' en cola hasta recibir el 001.")
        }
    }

    fun partFromChannel(channelName: String, partMessage: String? = null) {
        // Quitar la intención cancela también un JOIN pendiente
        channelsToRejoin.remove(channelName.lowercase())
        if (_isRegistered.value) {
            sendRaw("PART $channelName${partMessage?.let { " :$it" } ?: ""}")
            Log.i(TAG, "Enviando PART para el canal: $channelName${partMessage?.let { " con mensaje: $it"} ?: ""}")
        } else {
            Log.i(TAG, "Aún no registrado. PART de '$channelName' cancela el JOIN pendiente.")
        }
    }

    fun sendMessageToChannel(channel: String, message: String) {
        sendRaw("PRIVMSG $channel :$message")
    }

    private fun cleanupConnection() {
        if (!_connectionState.value && !transport.isOpen && !_isRegistered.value) return

        Log.i(TAG, "Limpiando conexión...")
        _isRegistered.value = false
        _connectionState.value = false
        transport.close()
    }

    private fun notifyConnectionLost() {
        if (!closedByUser && isActive) onConnectionLost()
    }

    /**
     * Envía `QUIT` y cierra la conexión. Espera a que el QUIT salga (como mucho [QUIT_TIMEOUT_MS])
     * antes de cerrar; antes se lanzaba en otra corrutina y la conexión se cerraba sin enviarlo.
     * No se puede cancelar a medias: una cancelación del llamante no deja la conexión abierta.
     */
    suspend fun disconnectAndCleanup(quitMessage: String = AppConstants.QUIT_MSG_CLIENT_SHUTDOWN) {
        closedByUser = true
        channelsToRejoin.clear()
        withContext(NonCancellable) {
            if (_connectionState.value && transport.isOpen) {
                withTimeoutOrNull(QUIT_TIMEOUT_MS) { sendRawInternal("QUIT :$quitMessage") }
            }
            cleanupConnection()
        }
        Log.i(TAG, "ManualIrcClient desconectado y limpiado.")
    }

    /**
     * Cierra un intento de conexión que no llegó a registrarse (sin 001), **conservando los canales**
     * para el siguiente intento. A diferencia de [disconnectAndCleanup], no envía QUIT (aún no estamos
     * registrados) ni olvida a qué canales hay que volver.
     */
    suspend fun abortAttempt() {
        closedByUser = true // quien aborta ya gestiona el reintento: no es una "pérdida de conexión"
        withContext(NonCancellable) { cleanupConnection() }
        Log.i(TAG, "Intento de conexión abortado; se conservan ${channelsToRejoin.size} canales para reintentar.")
    }
}
