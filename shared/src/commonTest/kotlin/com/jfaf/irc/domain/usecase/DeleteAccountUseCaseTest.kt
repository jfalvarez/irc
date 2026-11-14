package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.platform.AuthUser
import com.jfaf.irc.domain.platform.ReauthResult
import com.jfaf.irc.domain.user.UserMetadataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteAccountUseCaseTest {

    /** Registra el orden de las llamadas para comprobar que no se sigue tras un fallo. */
    private val calls = mutableListOf<String>()

    private inner class FakeAuth(
        user: AuthUser?,
        private val reauth: ReauthResult = ReauthResult.OK,
        private val deleteOk: Boolean = true
    ) : AuthSession {
        override val currentUser = MutableStateFlow(user)
        override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser? = null
        override fun signOut() { currentUser.value = null }
        override val supportsAccountDeletion = true
        override suspend fun reauthenticateWithGoogleIdToken(idToken: String): ReauthResult {
            calls += "reauth:$idToken"
            return reauth
        }
        override suspend fun deleteAccount(): Boolean {
            calls += "deleteAccount"
            if (deleteOk) currentUser.value = null
            return deleteOk
        }
    }

    private inner class FakeStore(private val deleteOk: Boolean = true) : UserMetadataStore {
        override val ignoredUsersFlow = flowOf(emptySet<String>())
        override val friendsFlow = flowOf(emptySet<String>())
        override suspend fun addIgnoredUser(nick: String) {}
        override suspend fun removeIgnoredUser(nick: String) {}
        override suspend fun addFriend(nick: String) {}
        override suspend fun removeFriend(nick: String) {}
        override suspend fun deleteAccountData(uid: String): Boolean {
            calls += "deleteData:$uid"
            return deleteOk
        }
    }

    private val ana = AuthUser("uid-ana", "ana@example.com")

    @Test
    fun todoBien_confirmaBorraListasYDespuesLaCuenta() = runBlocking {
        val auth = FakeAuth(ana)
        val result = DeleteAccountUseCase(auth, FakeStore())("token-nuevo")
        assertEquals(DeleteAccountResult.DELETED, result)
        assertEquals(listOf("reauth:token-nuevo", "deleteData:uid-ana", "deleteAccount"), calls)
        assertEquals(null, auth.currentUser.value)
    }

    @Test
    fun sinSesion_noHaceNada() = runBlocking {
        val result = DeleteAccountUseCase(FakeAuth(null), FakeStore())("token")
        assertEquals(DeleteAccountResult.NOT_SIGNED_IN, result)
        assertEquals(emptyList(), calls)
    }

    @Test
    fun otraCuentaDeGoogle_noBorraNada() = runBlocking {
        val result = DeleteAccountUseCase(FakeAuth(ana, reauth = ReauthResult.WRONG_ACCOUNT), FakeStore())("token")
        assertEquals(DeleteAccountResult.WRONG_ACCOUNT, result)
        assertEquals(listOf("reauth:token"), calls)
    }

    @Test
    fun falloAlConfirmar_noBorraNada() = runBlocking {
        val result = DeleteAccountUseCase(FakeAuth(ana, reauth = ReauthResult.FAILED), FakeStore())("token")
        assertEquals(DeleteAccountResult.REAUTH_FAILED, result)
        assertEquals(listOf("reauth:token"), calls)
    }

    @Test
    fun falloAlBorrarLasListas_noBorraLaCuenta() = runBlocking {
        val auth = FakeAuth(ana)
        val result = DeleteAccountUseCase(auth, FakeStore(deleteOk = false))("token")
        assertEquals(DeleteAccountResult.DATA_NOT_DELETED, result)
        assertEquals(listOf("reauth:token", "deleteData:uid-ana"), calls)
        assertEquals(ana, auth.currentUser.value)
    }

    @Test
    fun falloAlBorrarLaCuenta_seInforma() = runBlocking {
        val auth = FakeAuth(ana, deleteOk = false)
        val result = DeleteAccountUseCase(auth, FakeStore())("token")
        assertEquals(DeleteAccountResult.ACCOUNT_NOT_DELETED, result)
        assertEquals(ana, auth.currentUser.value)
    }
}
