package com.jfaf.irc.data.remote

import com.jfaf.irc.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/** [IrcTransport] sobre `java.net.Socket` / `SSLSocket`. Sirve en Android y en escritorio. */
class JvmSocketTransport : IrcTransport {
    private val TAG = "JvmSocketTransport"

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
    }

    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var reader: BufferedReader? = null

    override val isOpen: Boolean get() = socket != null

    /**
     * Con TLS hace el handshake y comprueba que el certificado corresponde a [host]:
     * un `SSLSocket` crudo valida la cadena de confianza pero NO el nombre del servidor.
     */
    override suspend fun open(host: String, port: Int, tls: Boolean) = withContext(Dispatchers.IO) {
        val plain = Socket()
        socket = plain // para que close() pueda cerrarlo si algo falla a mitad
        plain.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        var connected: Socket = plain
        if (tls) {
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
            connected = ssl
        }
        writer = BufferedWriter(OutputStreamWriter(connected.getOutputStream(), Charsets.UTF_8))
        reader = BufferedReader(InputStreamReader(connected.getInputStream(), Charsets.UTF_8))
    }

    override suspend fun readLine(): String? = withContext(Dispatchers.IO) {
        (reader ?: return@withContext null).readLine()
    }

    override suspend fun writeLine(line: String) = withContext(Dispatchers.IO) {
        val w = writer ?: error("Transporte cerrado")
        w.write("$line\r\n")
        w.flush()
    }

    override fun close() {
        try { writer?.close() } catch (e: Exception) { Log.e(TAG, "Error cerrando writer", e) }
        try { reader?.close() } catch (e: Exception) { Log.e(TAG, "Error cerrando reader", e) }
        try { socket?.close() } catch (e: Exception) { Log.e(TAG, "Error cerrando socket", e) }
        writer = null
        reader = null
        socket = null
    }
}
