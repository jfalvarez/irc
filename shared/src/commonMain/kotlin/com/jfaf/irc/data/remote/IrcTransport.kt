package com.jfaf.irc.data.remote

/**
 * Conexión de texto, línea a línea, con el servidor IRC. Es `suspend` para que una
 * implementación no bloqueante (p. ej. Ktor) encaje sin cambiar el cliente.
 */
interface IrcTransport {
    /** true mientras haya una conexión abierta o a medio abrir (recursos sin liberar). */
    val isOpen: Boolean

    /** Abre la conexión; con [tls], handshake y verificación del nombre. Lanza excepción si falla. */
    suspend fun open(host: String, port: Int, tls: Boolean)

    /** Siguiente línea sin el fin de línea, o null si el servidor cerró la conexión. */
    suspend fun readLine(): String?

    /** Envía [line] terminada en CRLF. */
    suspend fun writeLine(line: String)

    /** Libera la conexión. Idempotente y no lanza excepciones. */
    fun close()
}
