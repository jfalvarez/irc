package com.jfaf.irc.data.firestore

import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.platform.AuthUser
import com.jfaf.irc.domain.user.RemoteLists
import com.jfaf.irc.domain.user.UserLists
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirestoreRestUserListsTest {

    private class FakeSession(user: AuthUser?) : AuthSession {
        override val currentUser: StateFlow<AuthUser?> = MutableStateFlow(user)
        override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser? = null
        override fun signOut() {}
    }

    private val requests = MutableStateFlow<List<HttpRequestData>>(emptyList())
    @Volatile private var status = HttpStatusCode.OK
    @Volatile private var body = DOCUMENT
    @Volatile private var token: String? = "tok"
    @Volatile private var now = 0L
    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val http = HttpClient(MockEngine { request ->
        requests.update { it + request }
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    })

    private fun lists(user: AuthUser? = AuthUser("u1", null), pollIntervalMillis: Long = 60_000L) =
        FirestoreRestUserLists(http, "proyecto", FakeSession(user), { token }, { now },
            refreshRequests = refresh, pollIntervalMillis = pollIntervalMillis, minGapMillis = 30_000L)

    private fun gets() = requests.value.filter { it.method == HttpMethod.Get }

    @Test
    fun sinSesion_noHayListasNiPeticiones() = runBlocking {
        assertNull(lists(user = null).snapshots.first())
        assertTrue(requests.value.isEmpty())
    }

    @Test
    fun conSesion_leeElDocumentoDeLaCuentaConSuToken() = runBlocking {
        assertEquals(
            RemoteLists("u1", UserLists(ignored = setOf("pepe"), friends = setOf("ana", "luis"))),
            lists().snapshots.first()
        )
        val request = gets().single()
        assertEquals("/v1/projects/proyecto/databases/(default)/documents/users/u1", request.url.encodedPath.replace("%28", "(").replace("%29", ")"))
        assertEquals("firestore.googleapis.com", request.url.host)
        assertEquals("Bearer tok", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun documentoInexistenteOListasVacias_sonListasVacias() = runBlocking {
        status = HttpStatusCode.NotFound
        assertEquals(RemoteLists("u1", UserLists()), lists().snapshots.first())

        status = HttpStatusCode.OK
        body = """{"fields":{"friends":{"arrayValue":{}}}}"""
        assertEquals(RemoteLists("u1", UserLists()), lists().snapshots.first())
    }

    @Test
    fun errorDelServidorOSinToken_noHayListas() = runBlocking {
        status = HttpStatusCode.Forbidden
        assertNull(lists().snapshots.first())

        status = HttpStatusCode.OK
        token = null
        assertNull(lists().snapshots.first())
    }

    @Test
    fun vuelveAConsultarCadaIntervalo() = runBlocking {
        val received = MutableStateFlow(0)
        val job = Job()
        CoroutineScope(Dispatchers.Default + job).launch {
            lists(pollIntervalMillis = 100L).snapshots.collect { received.update { it + 1 } }
        }
        withTimeout(5_000) { received.first { it >= 3 } }
        job.cancelAndJoin()
    }

    @Test
    fun alVolverALaVentana_consultaSiHanPasadoTreintaSegundos() = runBlocking {
        val received = MutableStateFlow(0)
        val job = Job()
        CoroutineScope(Dispatchers.Default + job).launch {
            lists().snapshots.collect { received.update { it + 1 } }
        }
        withTimeout(5_000) {
            received.first { it == 1 }
            refresh.subscriptionCount.first { it > 0 }
            now = 10_000L // demasiado pronto: no consulta
            refresh.emit(Unit)
        }
        delay(300) // a que se procese el aviso
        assertEquals(1, gets().size)

        withTimeout(5_000) {
            now = 31_000L
            refresh.emit(Unit)
            received.first { it == 2 }
        }
        assertEquals(2, gets().size)
        job.cancelAndJoin()
    }

    @Test
    fun guardar_anadeYQuitaEnUnSoloCommit() = runBlocking {
        val ok = lists().apply("u1", UserLists(ignored = setOf("pepe")), UserLists(friends = setOf("ana")))
        assertTrue(ok)
        val request = requests.value.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/documents:commit"))
        assertEquals("Bearer tok", request.headers[HttpHeaders.Authorization])

        val writes = Json.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("writes").jsonArray
        assertEquals(2, writes.size)
        val add = writes[0].jsonObject
        assertEquals(
            "projects/proyecto/databases/(default)/documents/users/u1",
            add.getValue("update").jsonObject.getValue("name").jsonPrimitive.content
        )
        assertTrue(add.getValue("updateMask").jsonObject.getValue("fieldPaths").jsonArray.isEmpty())
        val addTransform = add.getValue("updateTransforms").jsonArray.single().jsonObject
        assertEquals("ignoredNicks", addTransform.getValue("fieldPath").jsonPrimitive.content)
        assertEquals("pepe", addTransform.getValue("appendMissingElements").jsonObject.getValue("values")
            .jsonArray.single().jsonObject.getValue("stringValue").jsonPrimitive.content)
        val removeTransform = writes[1].jsonObject.getValue("updateTransforms").jsonArray.single().jsonObject
        assertEquals("friends", removeTransform.getValue("fieldPath").jsonPrimitive.content)
        assertTrue(removeTransform.containsKey("removeAllFromArray"))
    }

    @Test
    fun guardarSinCambios_noHacePeticion() = runBlocking {
        assertTrue(lists().apply("u1", UserLists(), UserLists()))
        assertTrue(requests.value.isEmpty())
    }

    @Test
    fun siNoSePuedeGuardar_devuelveFalse() = runBlocking {
        status = HttpStatusCode.InternalServerError
        assertFalse(lists().apply("u1", UserLists(friends = setOf("ana")), UserLists()))
        status = HttpStatusCode.OK
        token = null
        assertFalse(lists().apply("u1", UserLists(friends = setOf("ana")), UserLists()))
    }

    private companion object {
        const val DOCUMENT = """{"name":"projects/proyecto/databases/(default)/documents/users/u1",
            "fields":{"ignoredNicks":{"arrayValue":{"values":[{"stringValue":"pepe"}]}},
            "friends":{"arrayValue":{"values":[{"stringValue":"ana"},{"stringValue":"luis"}]}}},
            "createTime":"2026-01-01T00:00:00Z"}"""
    }
}
