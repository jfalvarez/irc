package com.jfaf.irc.desktop

import com.jfaf.irc.log.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Inicio de sesión de Google para apps instaladas: abre el navegador del sistema y recoge la respuesta
 * en un puerto local de 127.0.0.1, con PKCE (S256) y `state`. Google no permite hacerlo dentro de un webview.
 * Docs: https://developers.google.com/identity/protocols/oauth2/native-app
 */
class GoogleLoopbackOAuth(
    private val http: HttpClient,
    private val clientId: String,
    private val clientSecret: String,
    private val openBrowser: (URI) -> Unit = ::openInSystemBrowser,
    private val timeoutMillis: Int = 5 * 60 * 1000,
    private val authEndpoint: String = "https://accounts.google.com/o/oauth2/v2/auth",
    private val tokenEndpoint: String = "https://oauth2.googleapis.com/token"
) {
    /**
     * Abre el navegador, espera a que el usuario elija la cuenta y devuelve el idToken de Google.
     * null si el usuario cancela o se agota el tiempo; lanza [IOException] si falla el intercambio del código.
     */
    suspend fun requestIdToken(): String? {
        val verifier = randomToken(32)
        val state = randomToken(16)
        val (code, redirectUri) = withContext(Dispatchers.IO) {
            ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { server ->
                // accept() no atiende a la cancelación: al cancelar se cierra el socket y accept() lanza.
                val closer = launch {
                    try { awaitCancellation() } finally { server.close() }
                }
                try {
                    server.soTimeout = timeoutMillis
                    val redirectUri = "http://127.0.0.1:${server.localPort}"
                    openBrowser(authorizationUri(redirectUri, challengeFor(verifier), state))
                    waitForRedirect(server, state)?.let { it to redirectUri }
                } catch (e: SocketTimeoutException) {
                    Log.i(TAG, "Inicio de sesión de Google sin respuesta: tiempo agotado.")
                    null
                } catch (e: SocketException) {
                    ensureActive() // cerrado por la cancelación: termina como cancelado, no como error
                    throw e
                } finally {
                    closer.cancel()
                }
            }
        } ?: return null
        return exchangeCode(code, verifier, redirectUri)
    }

    internal fun authorizationUri(redirectUri: String, challenge: String, state: String): URI {
        val query = listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to "openid email profile",
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "prompt" to "select_account"
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8)}" }
        return URI("$authEndpoint?$query")
    }

    /** Atiende peticiones hasta la que trae nuestro `state`; devuelve su `code` (null si el usuario no aceptó). */
    private fun waitForRedirect(server: ServerSocket, state: String): String? {
        while (true) {
            val socket = server.accept()
            try {
                socket.soTimeout = 10_000
                val requestLine = socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine().orEmpty()
                val params = parseQuery(requestLine.split(' ').getOrNull(1).orEmpty())
                if (params["state"] != state) {
                    respond(socket, "404 Not Found", "") // p. ej. /favicon.ico
                    continue
                }
                val code = params["code"]
                respond(socket, "200 OK", if (code != null) SUCCESS_PAGE else CANCELLED_PAGE)
                if (code == null) Log.i(TAG, "Inicio de sesión de Google cancelado: ${params["error"]}")
                return code
            } finally {
                socket.close()
            }
        }
    }

    private suspend fun exchangeCode(code: String, verifier: String, redirectUri: String): String {
        val response = http.submitForm(
            url = tokenEndpoint,
            formParameters = parameters {
                append("code", code)
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("redirect_uri", redirectUri)
                append("grant_type", "authorization_code")
                append("code_verifier", verifier)
            }
        )
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw IOException("Google respondió ${response.status.value} al pedir el token: $body")
        return Json.parseToJsonElement(body).jsonObject["id_token"]?.jsonPrimitive?.content
            ?: throw IOException("La respuesta de Google no trae id_token")
    }

    private fun respond(socket: Socket, status: String, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray(Charsets.US_ASCII))
            write(body)
            flush()
        }
    }

    internal companion object {
        private const val TAG = "GoogleLoopbackOAuth"
        private val random = SecureRandom()

        private const val SUCCESS_PAGE = "<!doctype html><meta charset=\"utf-8\"><title>IRC</title>" +
            "<body style=\"font-family:sans-serif;text-align:center;margin-top:4em\">" +
            "<h2>Sesión iniciada</h2><p>Ya puedes cerrar esta pestaña y volver a IRC.</p>"
        private const val CANCELLED_PAGE = "<!doctype html><meta charset=\"utf-8\"><title>IRC</title>" +
            "<body style=\"font-family:sans-serif;text-align:center;margin-top:4em\">" +
            "<h2>No se inició sesión</h2><p>Puedes cerrar esta pestaña y volver a IRC.</p>"

        fun randomToken(bytes: Int): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))

        /** code_challenge de PKCE con S256. */
        fun challengeFor(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        fun parseQuery(target: String): Map<String, String> = target.substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { pair ->
                val (key, value) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
            }
    }
}

/** Abre [uri] en el navegador predeterminado (en Linux sin soporte de AWT, con xdg-open). */
fun openInSystemBrowser(uri: URI) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(uri)
    } else {
        ProcessBuilder("xdg-open", uri.toString()).start()
    }
}
