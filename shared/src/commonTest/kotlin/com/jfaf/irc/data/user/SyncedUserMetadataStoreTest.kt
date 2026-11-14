package com.jfaf.irc.data.user

import com.jfaf.irc.data.prefs.USER_PREFERENCES_FILE
import com.jfaf.irc.data.prefs.createPreferencesDataStore
import com.jfaf.irc.domain.user.RemoteLists
import com.jfaf.irc.domain.user.RemoteUserLists
import com.jfaf.irc.domain.user.UserLists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncedUserMetadataStoreTest {

    /** Cuenta de pega: guarda lo que se le envía y deja simular respuestas del servidor. */
    private class FakeRemote : RemoteUserLists {
        override val snapshots = MutableStateFlow<RemoteLists?>(null)
        private val applied = MutableStateFlow<List<Triple<String, UserLists, UserLists>>>(emptyList())
        override fun apply(uid: String, add: UserLists, remove: UserLists) {
            applied.update { it + Triple(uid, add, remove) }
        }
        fun appliedNow() = applied.value
    }

    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "irc-lists-test-${Random.nextLong().toULong()}"
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val local = LocalUserMetadataStore(createPreferencesDataStore((dir / USER_PREFERENCES_FILE).toString(), scope))
    private val remote = FakeRemote()
    private val store = SyncedUserMetadataStore(local, remote)

    @AfterTest
    fun cleanUp() {
        runBlocking { job.cancelAndJoin() }
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    private fun signIn(uid: String, lists: UserLists) = runBlocking {
        remote.snapshots.value = RemoteLists(uid, lists)
        withTimeout(5_000) { store.syncAccount.first { it == uid } }
    }

    private fun signOut() = runBlocking {
        remote.snapshots.value = null
        withTimeout(5_000) { store.syncAccount.first { it == null } }
    }

    @Test
    fun sinSesion_amigosEIgnoradosSeGuardanEnLocal() = runBlocking {
        store.start(scope)
        store.addFriend("Ana")
        store.addIgnoredUser("Pepe")
        assertEquals(setOf("ana"), store.friendsFlow.first())
        assertEquals(setOf("pepe"), store.ignoredUsersFlow.first())
        assertTrue(remote.appliedNow().isEmpty())
    }

    @Test
    fun alIniciarSesionPorPrimeraVez_subeLoLocalYJuntaConLaCuenta() = runBlocking {
        store.start(scope)
        store.addFriend("ana")
        signIn("u1", UserLists(friends = setOf("luis")))
        assertEquals(setOf("ana", "luis"), store.friendsFlow.first())
        assertEquals(listOf(Triple("u1", UserLists(friends = setOf("ana")), UserLists())), remote.appliedNow())
    }

    @Test
    fun conSesion_cadaCambioVaALaCuentaYLoRemotoSustituyeALoLocal() = runBlocking {
        store.start(scope)
        signIn("u1", UserLists(ignored = setOf("pepe")))
        store.addFriend("Ana")
        assertEquals(listOf(Triple("u1", UserLists(friends = setOf("ana")), UserLists())), remote.appliedNow())

        // Otro dispositivo quita a "pepe": llega una nueva versión de la cuenta
        remote.snapshots.value = RemoteLists("u1", UserLists(friends = setOf("ana")))
        withTimeout(5_000) { store.ignoredUsersFlow.first { it.isEmpty() } }
        assertEquals(setOf("ana"), store.friendsFlow.first())
    }

    @Test
    fun alCerrarSesion_seConservanLasListasYLoCambiadoSeSubeAlVolver() = runBlocking {
        store.start(scope)
        signIn("u1", UserLists(friends = setOf("ana")))
        signOut()
        assertEquals(setOf("ana"), store.friendsFlow.first())

        store.removeFriend("ana")
        store.addIgnoredUser("pepe")
        assertTrue(remote.appliedNow().isEmpty())

        signIn("u1", UserLists(friends = setOf("ana")))
        assertEquals(
            listOf(Triple("u1", UserLists(ignored = setOf("pepe")), UserLists(friends = setOf("ana")))),
            remote.appliedNow()
        )
        assertEquals(emptySet(), store.friendsFlow.first())
        assertEquals(setOf("pepe"), store.ignoredUsersFlow.first())
    }

    @Test
    fun conOtraCuenta_mandaLaCuentaNueva() = runBlocking {
        store.start(scope)
        signIn("u1", UserLists(friends = setOf("ana")))
        signOut()
        store.addIgnoredUser("pepe")

        signIn("u2", UserLists(friends = setOf("luis")))
        assertEquals(setOf("luis"), store.friendsFlow.first())
        assertEquals(emptySet(), store.ignoredUsersFlow.first())
        assertTrue(remote.appliedNow().isEmpty())
    }
}
