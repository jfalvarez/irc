package com.jfaf.irc

import android.util.Log
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
import javax.net.ssl.SSLSocketFactory

class ManualIrcClient(
    val host: String, // <--- CAMBIO AQUÍ: 'private' eliminado
    private val port: Int,
    private val useSsl: Boolean,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG = "ManualIrcClient"

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    private val _incomingMessages = MutableSharedFlow<ParsedIrcMessage>()
    val incomingMessages: SharedFlow<ParsedIrcMessage> = _incomingMessages.asSharedFlow()

    private val _connectionState = MutableStateFlow<Boolean>(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private var currentNickname: String = "ManualBot"

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
        val username = user
        val realname = real

        coroutineScope.launch(ioDispatcher) {
            _isRegistered.value = false // Asegurar que esté reiniciado para una nueva conexión
            try {
                Log.d(TAG, "Intentando conectar a $host:$port (SSL: $useSsl), Nick: $currentNickname")
                socket = if (useSsl) {
                    SSLSocketFactory.getDefault().createSocket()
                } else {
                    Socket()
                }
                socket?.connect(InetSocketAddress(host, port), 10000)

                if (socket?.isConnected == true) {
                    writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream(), Charsets.UTF_8))
                    reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), Charsets.UTF_8))
                    _connectionState.value = true
                    Log.i(TAG, "Socket conectado a $host:$port.")

                    sendRawInternal("NICK $currentNickname")
                    sendRawInternal("USER $username 0 * :$realname")

                    listenForMessages()
                } else {
                    throw Exception("Fallo al conectar el socket (después de timeout o error).")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error de conexión: ${e.message}", e)
                cleanupConnection()
            }
        }
    }

    private fun parseRawLineToIrcMessage(rawLine: String): ParsedIrcMessage {
        var mutableLine = rawLine
        val prefix: String?

        if (mutableLine.startsWith(":")) {
            val prefixEnd = mutableLine.indexOf(' ')
            prefix = mutableLine.substring(1, prefixEnd)
            mutableLine = mutableLine.substring(prefixEnd + 1)
        } else {
            prefix = null
        }

        val trailing: String?
        val paramsList = mutableListOf<String>()

        val trailingStart = mutableLine.indexOf(" :")
        if (trailingStart != -1) {
            trailing = mutableLine.substring(trailingStart + 2)
            mutableLine = mutableLine.substring(0, trailingStart)
        } else {
            trailing = null
        }

        val commandAndParams = mutableLine.split(' ')
        val command = commandAndParams.firstOrNull() ?: "UNKNOWN"
        
        if (commandAndParams.size > 1) {
            paramsList.addAll(commandAndParams.subList(1, commandAndParams.size).filter { it.isNotEmpty() })
        }

        return ParsedIrcMessage(
            rawLine = rawLine,
            prefix = prefix,
            command = command,
            params = paramsList,
            trailing = trailing
        )
    }

    private suspend fun listenForMessages() {
        try {
            while (isActive && _connectionState.value && reader != null) {
                val line = reader?.readLine()
                if (line != null) {
                    Log.d(TAG, "<<< $line")
                    val parsedMessage = parseRawLineToIrcMessage(line)
                    _incomingMessages.emit(parsedMessage)
                    handleInternalMessages(parsedMessage)
                } else {
                    Log.w(TAG, "readLine devolvió null. Conexión cerrada por el servidor.")
                    cleanupConnection()
                    break
                }
            }
        } catch (e: Exception) {
            if (isActive) {
                Log.e(TAG, "Error leyendo del socket: ${e.message}", e)
            }
            cleanupConnection()
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
        if (message.command == "001") { // RPL_WELCOME
            _isRegistered.value = true
            val confirmedNick = message.params.firstOrNull() ?: currentNickname
            Log.i(TAG, "Cliente registrado con el servidor (RPL_WELCOME recibido). Nick confirmado: $confirmedNick")
            // Podríamos querer actualizar currentNickname si el servidor lo cambió (ej. por colisión)
            // this.currentNickname = confirmedNick 
        }
        // Add other internal handling here if needed
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
            writer?.write("$message\r\n")
            writer?.flush()
            Log.d(TAG, ">>> $message")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando mensaje: ${e.message}", e)
            cleanupConnection()
        }
    }

    fun joinChannel(channel: String) {
        if (_isRegistered.value) {
            sendRaw("JOIN $channel")
        } else {
            Log.w(TAG, "Aún no registrado con el servidor. Comando JOIN para '$channel' no enviado.")
            // Aquí se podría encolar el comando para enviarlo más tarde
        }
    }

    // Nueva función para salir de un canal
    fun partFromChannel(channelName: String) {
        if (_isRegistered.value) {
            // Asumimos que channelName ya incluye el prefijo # si es necesario, o que el servidor lo maneja.
            // Puedes añadir un mensaje de salida opcional: "PART $channelName :Saliendo"
            sendRaw("PART $channelName")
            Log.i(TAG, "Enviando PART para el canal: $channelName")
        } else {
            Log.w(TAG, "Aún no registrado con el servidor. Comando PART para '$channelName' no enviado.")
        }
    }

    fun sendMessageToChannel(channel: String, message: String) {
        sendRaw("PRIVMSG $channel :$message")
    }

    fun quitServer(quitMessage: String = "Leaving") {
        sendRaw("QUIT :$quitMessage")
    }

    private fun cleanupConnection() {
        if (!_connectionState.value && socket == null && !_isRegistered.value) return // Check all states

        Log.i(TAG, "Limpiando conexión...")
        _isRegistered.value = false // Reset registration state
        _connectionState.value = false
        try { writer?.close() } catch (e: Exception) { Log.e(TAG, "Error cerrando writer", e) }
        try { reader?.close() } catch (e: Exception) { Log.e(TAG, "Error cerrando reader", e) }
        try { socket?.close() } catch (e: Exception) { Log.e(TAG, "Error cerrando socket", e) }
        writer = null
        reader = null
        socket = null
    }

    fun disconnectAndCleanup() {
        if (_connectionState.value) {
            // Intenta enviar QUIT solo si hay una conexión activa y escritor disponible.
            // Esto es más seguro que solo chequear _connectionState.value
            if (writer != null) { 
                quitServer("Cliente cerrándose")
            }
        }
        cleanupConnection()
        Log.i(TAG, "ManualIrcClient desconectado y limpiado.")
    }
    
}
