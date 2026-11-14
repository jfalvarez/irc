package com.jfaf.irc.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirebaseRestAuthTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun auth(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): FirebaseRestAuth {
        val engine = MockEngine { request -> requests += request; handler(request) }
        return FirebaseRestAuth(HttpClient(engine), apiKey = "clave", now = { 1_000L })
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.jsonBody() = Json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun entrarConGoogle_enviaElIdTokenYDevuelveLaSesion() = runBlocking {
        val tokens = auth {
            json("""{"localId":"u1","email":"ana@example.com","idToken":"fb","refreshToken":"r1","expiresIn":"3600","extra":1}""")
        }.signInWithGoogleIdToken("google-token")

        assertEquals(FirebaseTokens("u1", "ana@example.com", "fb", "r1", 1_000L + 3_600_000L), tokens)
        val request = requests.single()
        assertEquals("https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=clave", request.url.toString())
        val body = request.jsonBody()
        assertEquals("id_token=google-token&providerId=google.com", body["postBody"]?.jsonPrimitive?.content)
        assertEquals("true", body["returnSecureToken"]?.jsonPrimitive?.content)
    }

    @Test
    fun renovar_usaElRefreshTokenYConservaElCorreo() = runBlocking {
        val tokens = auth {
            json("""{"user_id":"u1","id_token":"fb2","refresh_token":"r2","expires_in":"3600","token_type":"Bearer"}""")
        }.refresh("r1", "ana@example.com")

        assertEquals(FirebaseTokens("u1", "ana@example.com", "fb2", "r2", 1_000L + 3_600_000L), tokens)
        val request = requests.single()
        assertEquals("https://securetoken.googleapis.com/v1/token?key=clave", request.url.toString())
        assertEquals("refresh_token", request.jsonBody()["grant_type"]?.jsonPrimitive?.content)
        assertEquals("r1", request.jsonBody()["refresh_token"]?.jsonPrimitive?.content)
    }

    @Test
    fun renovarConTokenRevocado_esSesionInvalida() = runBlocking {
        val error = assertFailsWith<FirebaseAuthException> {
            auth { json("""{"error":{"code":400,"message":"TOKEN_EXPIRED"}}""", HttpStatusCode.BadRequest) }.refresh("r1", null)
        }
        assertTrue(error.invalidCredentials)
        assertTrue(error.message!!.contains("TOKEN_EXPIRED"))
    }

    @Test
    fun errorDeServidorOClaveMala_noInvalidaLaSesion() = runBlocking {
        val server = assertFailsWith<FirebaseAuthException> {
            auth { json("{}", HttpStatusCode.ServiceUnavailable) }.refresh("r1", null)
        }
        assertFalse(server.invalidCredentials)

        val badKey = assertFailsWith<FirebaseAuthException> {
            auth { json("""{"error":{"message":"API key not valid"}}""", HttpStatusCode.Forbidden) }.refresh("r1", null)
        }
        assertFalse(badKey.invalidCredentials)
    }

    @Test
    fun sinRed_esErrorRecuperable() = runBlocking {
        val error = assertFailsWith<FirebaseAuthException> {
            auth { throw RuntimeException("sin red") }.refresh("r1", null)
        }
        assertFalse(error.invalidCredentials)
    }
}
