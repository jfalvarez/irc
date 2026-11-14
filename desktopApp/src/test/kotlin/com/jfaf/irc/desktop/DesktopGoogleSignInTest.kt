package com.jfaf.irc.desktop

import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.platform.AuthUser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.URI
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopGoogleSignInTest {

    private class FakeAuth(private val accept: Boolean) : AuthSession {
        val tokens: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override val currentUser: StateFlow<AuthUser?> = MutableStateFlow(null)
        override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser? {
            tokens += idToken
            return if (accept) AuthUser("u1", null) else null
        }
        override fun signOut() {}
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val errors: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val http = HttpClient(MockEngine {
        respond("""{"id_token":"google-id"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    })

    @AfterTest
    fun tearDown() = scope.cancel()

    /** Navegador de pega que contesta a la redirección con [answer]. */
    private fun browser(answer: (state: String) -> String): (URI) -> Unit = { uri ->
        val params = GoogleLoopbackOAuth.parseQuery("?${uri.rawQuery}")
        thread {
            val url = "${params.getValue("redirect_uri")}/?${answer(params.getValue("state"))}"
            (URI(url).toURL().openConnection() as java.net.HttpURLConnection).responseCode
        }
    }

    private fun signIn(auth: AuthSession, answer: (String) -> String) =
        DesktopGoogleSignIn(GoogleLoopbackOAuth(http, "cliente", "secreto", browser(answer)), auth, scope) { errors += it }

    @Test
    fun aceptado_entraEnFirebaseConElIdTokenDeGoogle() = runBlocking {
        val auth = FakeAuth(accept = true)
        withTimeout(10_000) { signIn(auth) { "state=$it&code=c" }.start().join() }
        assertEquals(listOf("google-id"), auth.tokens)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun siFirebaseLoRechaza_avisa() = runBlocking {
        withTimeout(10_000) { signIn(FakeAuth(accept = false)) { "state=$it&code=c" }.start().join() }
        assertEquals(1, errors.size)
    }

    @Test
    fun siElUsuarioCancelaEnElNavegador_noAvisa() = runBlocking {
        val auth = FakeAuth(accept = true)
        withTimeout(10_000) { signIn(auth) { "state=$it&error=access_denied" }.start().join() }
        assertTrue(auth.tokens.isEmpty())
        assertTrue(errors.isEmpty())
    }
}
