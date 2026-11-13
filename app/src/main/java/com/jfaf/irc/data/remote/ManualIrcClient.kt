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
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class ManualIrcClient(
    val host: String,
    private val port: Int,
    private val useSsl: Boolean,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Se invoca cuando la conexión se cierra sin que el usuario lo haya pedido. */
    private val onConnectionLost: () -> Unit = {}
) {
    private val TAG = "ManualIrcClient"

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
    }

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

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
        get() = _connectionState.value && socket?.isConnected == true

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
                openSocket()

                if (socket?.isConnected == true) {
                    writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream(), Charsets.UTF_8))
                    reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), Charsets.UTF_8))
                    _connectionState.value = true
                    Log.i(TAG, "Socket conectado a $host:$port.")

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

    /**
     * Abre [socket]. Con TLS hace el handshake y comprueba que el certificado corresponde a [host]:
     * un `SSLSocket` crudo valida la cadena de confianza pero NO el nombre del servidor.
     */
    private fun openSocket() {
        val plain = Socket()
        socket = plain // para que cleanupConnection() pueda cerrarlo si algo falla a mitad
        plain.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        if (!useSsl) return

        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        // Envuelve el socket ya conectado: así se envía SNI y el handshake conoce el nombre de host.
        val ssl = factory.createSocket(plain, host, port, true) as SSLSocket
        socket = ssl
        ssl.soTimeout = CONNECT_TIMEOUT_MS // límite también para el handshake
        ssl.startHandshake()
        val verifier = HttpsURLConnection.getDefaultHostnameVerifier()
        val names = TlsHostPolicy.acceptedCertificateHosts(host)
        if (names.none { verifier.verify(it, ssl.session) }) {
            throw SSLPeerUnverifiedException("El certificado del servidor no corresponde a $host (se esperaba: ${names.joinToString()})")
        }
        ssl.soTimeout = 0
    }

    private suspend fun listenForMessages() {
        try {
            while (isActive && _connectionState.value) {
                val currentReader = reader ?: break
                // readLine() bloquea: se ejecuta explícitamente en el dispatcher de IO
                val line = withContext(ioDispatcher) { currentReader.readLine() }
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
            if (isActive) {
                Log.e(TAG, "Error leyendo del socket: ${e.message}", e)
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
        if (!_connectionState.value || writer == null) {
            Log.w(TAG, "No conectado, no se puede enviar: $message")
            return
        }
        coroutineScope.launch(ioDispatcher) {
             sendRawInternal(message)
        }
    }

    private suspend fun sendRawInternal(message: String) {
        if (!_connectionState.value || writer == null) {
            Log.w(TAG, "No conectado, no se puede enviar (internal): $message")
            return
        }
        try {
            // write/flush bloquean: se ejecutan explícitamente en el dispatcher de IO
            withContext(ioDispatcher) {
                writer?.write("$message\r\n")
                writer?.flush()
            }
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

    fun quitServer(quitMessage: String = AppConstants.DEFAULT_QUIT_MESSAGE) {
        sendRaw("QUIT :$quitMessage")
    }

    private fun cleanupConnection() {
        if (!_connectionState.value && socket == null && !_isRegistered.value) return

        Log.i(TAG, "Limpiando conexión...")
        _isRegistered.value = false
        _connectionState.value = false
        try { writer?.close() } catch (e: Exception) { Log.e(TAG, "Error cerrando writer", e) }
        try { reader?.close() } catch (e: Exception) { Log.e(TAG, "Error cerrando reader", e) }
        try { socket?.close() } catch (e: Exception) { Log.e(TAG, "Error cerrando socket", e) }
        writer = null
        reader = null
        socket = null
    }

    private fun notifyConnectionLost() {
        if (!closedByUser && isActive) onConnectionLost()
    }

    fun disconnectAndCleanup() {
        closedByUser = true
        channelsToRejoin.clear()
        if (_connectionState.value) {
            if (writer != null) { 
                quitServer(AppConstants.QUIT_MSG_CLIENT_SHUTDOWN)
            }
        }
        cleanupConnection()
        Log.i(TAG, "ManualIrcClient desconectado y limpiado.")
    }
    
}
