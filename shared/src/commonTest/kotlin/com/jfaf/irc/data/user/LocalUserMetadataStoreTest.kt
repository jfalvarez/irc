package com.jfaf.irc.data.user

import com.jfaf.irc.data.prefs.createPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalUserMetadataStoreTest {

    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "irc-lists-test-${Random.nextLong().toULong()}"
    private val file: Path = dir / "lists.preferences_pb"

    @AfterTest
    fun cleanUp() = FileSystem.SYSTEM.deleteRecursively(dir)

    @Test
    fun nicksEnMinusculasYSinDuplicados() = runBlocking {
        val job = Job()
        val store = LocalUserMetadataStore(createPreferencesDataStore(file.toString(), CoroutineScope(Dispatchers.IO + job)))
        store.addIgnoredUser("PePe")
        store.addIgnoredUser("pepe")
        store.addFriend("Ana")
        assertEquals(setOf("pepe"), store.ignoredUsersFlow.first())
        assertEquals(setOf("ana"), store.friendsFlow.first())
        job.cancelAndJoin()
    }

    @Test
    fun quitarSoloAfectaALaListaIndicadaYSinDistinguirMayusculas() = runBlocking {
        val job = Job()
        val store = LocalUserMetadataStore(createPreferencesDataStore(file.toString(), CoroutineScope(Dispatchers.IO + job)))
        store.addIgnoredUser("bob")
        store.addFriend("bob")
        store.removeIgnoredUser("BOB")
        assertEquals(emptySet(), store.ignoredUsersFlow.first())
        assertEquals(setOf("bob"), store.friendsFlow.first())
        job.cancelAndJoin()
    }

    @Test
    fun laListaSeConservaAlReabrirElAlmacen() = runBlocking {
        val job1 = Job()
        val first = LocalUserMetadataStore(createPreferencesDataStore(file.toString(), CoroutineScope(Dispatchers.IO + job1)))
        first.addIgnoredUser("troll")
        first.addFriend("ana")
        job1.cancelAndJoin() // como al cerrar la app: sólo un DataStore activo por archivo

        val job2 = Job()
        val second = LocalUserMetadataStore(createPreferencesDataStore(file.toString(), CoroutineScope(Dispatchers.IO + job2)))
        assertEquals(setOf("troll"), second.ignoredUsersFlow.first())
        assertEquals(setOf("ana"), second.friendsFlow.first())
        job2.cancelAndJoin()
    }
}
