package com.jfaf.irc.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URI
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El flujo completo sin red: un «navegador» de pega pide la redirección al puerto local como haría Google. */
class GoogleLoopbackOAuthTest {

    private val tokenRequests: MutableList<Parameters> = Collections.synchronizedList(mutableListOf())
    private val pages: MutableList<Pair<Int, String>> = Collections.synchronizedList(mutableListOf())

    private val http = HttpClient(MockEngine { request ->
        tokenRequests += (request.body as FormDataContent).formData
        respond("""{"id_token":"google-id","access_token":"a","expires_in":3599}""", HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json"))
    })

    private fun oauth(browser: (URI) -> Unit, timeoutMillis: Int = 10_000) =
        GoogleLoopbackOAuth(http, "cliente", "secreto", browser, timeoutMillis,
            authEndpoint = "https://accounts.example/auth", tokenEndpoint = "https://tokens.example/token")

    private fun get(url: String) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        val status = connection.responseCode
        val body = (if (status < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.readText().orEmpty()
        pages += status to body
    }

    /** Navegador que responde con [answer] (sin el `?`) a partir de los parámetros de la URL de Google. */
    private fun browser(answer: (Map<String, String>) -> String): (URI) -> Unit = { uri ->
        val params = GoogleLoopbackOAuth.parseQuery("?${uri.rawQuery}")
        val redirect = params.getValue("redirect_uri")
        thread {
            get("$redirect/favicon.ico") // el navegador pide más cosas: se ignoran
            get("$redirect/?state=otro&code=intruso") // y no vale cualquier state
            get("$redirect/?${answer(params)}")
        }
    }

    @Test
    fun aceptar_cambiaElCodigoPorElIdTokenConPkce() = runBlocking {
        var authParams = emptyMap<String, String>()
        val idToken = oauth(browser { params ->
            authParams = params
            "state=${params["state"]}&code=codigo%2F1&scope=email"
        }).requestIdToken()

        assertEquals("google-id", idToken)
        assertEquals("cliente", authParams["client_id"])
        assertEquals("S256", authParams["code_challenge_method"])
        assertEquals("openid email profile", authParams["scope"])
        assertTrue(authParams.getValue("redirect_uri").startsWith("http://127.0.0.1:"))

        val form = tokenRequests.single()
        assertEquals("codigo/1", form["code"])
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("secreto", form["client_secret"])
        assertEquals(authParams["redirect_uri"], form["redirect_uri"])
        assertEquals(authParams["code_challenge"], GoogleLoopbackOAuth.challengeFor(form["code_verifier"]!!))

        withTimeout(5_000) { while (pages.size < 3) delay(20) }
        assertEquals(listOf(404, 404, 200), pages.map { it.first })
        assertTrue(pages.last().second.contains("Sesión iniciada"))
    }

    @Test
    fun siElUsuarioNoAcepta_noHayTokenNiIntercambio() = runBlocking {
        val idToken = oauth(browser { params -> "state=${params["state"]}&error=access_denied" }).requestIdToken()
        assertNull(idToken)
        assertTrue(tokenRequests.isEmpty())
    }

    @Test
    fun sinRespuesta_seAgotaElTiempo() = runBlocking {
        assertNull(oauth(browser = {}, timeoutMillis = 300).requestIdToken())
    }

    @Test
    fun cancelar_cierraElPuertoSinEsperarAlTiempoMaximo() = runBlocking {
        val attempt = async { oauth(browser = {}, timeoutMillis = 60_000).requestIdToken() }
        delay(300)
        withTimeout(5_000) {
            attempt.cancel()
            attempt.join()
        }
        assertTrue(attempt.isCancelled)
    }
}
