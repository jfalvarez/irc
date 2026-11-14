package com.jfaf.irc.data.auth

import com.jfaf.irc.domain.platform.AuthUser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestAuthSessionTest {

    private class MemoryStore(var session: StoredSession? = null) : SessionStore {
        override fun load() = session
        override fun save(session: StoredSession) { this.session = session }
        override fun clear() { session = null }
    }

    private class FakeApi : FirebaseAuthApi {
        var refreshes = 0
        var refreshError: FirebaseAuthException? = null
        var nextExpiry = HOUR

        override suspend fun signInWithGoogleIdToken(googleIdToken: String) =
            if (googleIdToken == "malo") throw FirebaseAuthException("rechazado", invalidCredentials = false)
            else FirebaseTokens("u1", "ana@example.com", "fb-0", "r-0", nextExpiry)

        override suspend fun refresh(refreshToken: String, email: String?): FirebaseTokens {
            refreshError?.let { throw it }
            refreshes++
            return FirebaseTokens("u1", email, "fb-$refreshes", "r-$refreshes", nextExpiry)
        }
    }

    private var now = 0L
    private val api = FakeApi()

    @Test
    fun entrar_guardaLaSesionYPublicaElUsuario() = runBlocking {
        val store = MemoryStore()
        val session = RestAuthSession(api, store) { now }
        assertEquals(AuthUser("u1", "ana@example.com"), session.signInWithGoogleIdToken("google"))
        assertEquals(AuthUser("u1", "ana@example.com"), session.currentUser.value)
        assertEquals(StoredSession("u1", "ana@example.com", "r-0"), store.session)
        assertEquals("fb-0", session.idToken()) // vigente: no renueva
        assertEquals(0, api.refreshes)
    }

    @Test
    fun siFirebaseRechazaElToken_noHaySesion() = runBlocking {
        val store = MemoryStore()
        val session = RestAuthSession(api, store) { now }
        assertNull(session.signInWithGoogleIdToken("malo"))
        assertNull(session.currentUser.value)
        assertNull(store.session)
    }

    @Test
    fun alArrancar_restauraLaSesionYRenuevaConElTokenGuardado() = runBlocking {
        val store = MemoryStore(StoredSession("u1", "ana@example.com", "r-guardado"))
        val session = RestAuthSession(api, store) { now }
        assertEquals(AuthUser("u1", "ana@example.com"), session.currentUser.value)

        assertEquals("fb-1", session.idToken())
        assertEquals(StoredSession("u1", "ana@example.com", "r-1"), store.session)
    }

    @Test
    fun renuevaCuandoQuedanMenosDeCincoMinutos() = runBlocking {
        val session = RestAuthSession(api, MemoryStore()) { now }
        session.signInWithGoogleIdToken("google")
        now = HOUR - 6 * MINUTE
        assertEquals("fb-0", session.idToken())
        now = HOUR - 4 * MINUTE
        api.nextExpiry = now + HOUR
        assertEquals("fb-1", session.idToken())
        assertEquals("fb-1", session.idToken())
        assertEquals(1, api.refreshes)
    }

    @Test
    fun sesionRevocada_seCierraYSeBorra() = runBlocking {
        val store = MemoryStore(StoredSession("u1", null, "r-guardado"))
        val session = RestAuthSession(api, store) { now }
        api.refreshError = FirebaseAuthException("TOKEN_EXPIRED", invalidCredentials = true)
        assertNull(session.idToken())
        assertNull(session.currentUser.value)
        assertNull(store.session)
    }

    @Test
    fun sinRed_seMantieneLaSesion() = runBlocking {
        val store = MemoryStore(StoredSession("u1", null, "r-guardado"))
        val session = RestAuthSession(api, store) { now }
        api.refreshError = FirebaseAuthException("sin red", invalidCredentials = false)
        assertNull(session.idToken())
        assertEquals(AuthUser("u1", null), session.currentUser.value)

        api.refreshError = null
        assertEquals("fb-1", session.idToken()) // vuelve la red
    }

    @Test
    fun cerrarSesion_borraTodo() = runBlocking {
        val store = MemoryStore()
        val session = RestAuthSession(api, store) { now }
        session.signInWithGoogleIdToken("google")
        session.signOut()
        assertNull(session.currentUser.value)
        assertNull(store.session)
        assertNull(session.idToken())
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
