package com.jfaf.irc.data.prefs

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
import kotlin.test.assertTrue

class PreferencesDataStoreTest {

    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "irc-prefs-test-${Random.nextLong().toULong()}"
    private val file: Path = dir / "datastore" / USER_PREFERENCES_FILE

    @AfterTest
    fun cleanUp() = FileSystem.SYSTEM.deleteRecursively(dir)

    @Test
    fun ajustesSeConservanAlReabrir() = runBlocking {
        val job1 = Job()
        val first = UserPreferencesRepository(createPreferencesDataStore(file.toString(), CoroutineScope(Dispatchers.IO + job1)))
        first.updateNickname("ana")
        first.updateServerConfig(ServerConfig("irc.example.org", 6668, 6698))
        job1.cancelAndJoin() // como al cerrar la app: sólo un DataStore activo por archivo

        assertTrue(FileSystem.SYSTEM.exists(file))

        val job2 = Job()
        val second = UserPreferencesRepository(createPreferencesDataStore(file.toString(), CoroutineScope(Dispatchers.IO + job2)))
        assertEquals("ana", second.userPreferencesFlow.first().nickname)
        assertEquals(ServerConfig("irc.example.org", 6668, 6698), second.serverConfigFlow.first())
        job2.cancelAndJoin()
    }

    @Test
    fun sinArchivo_valoresPorDefecto() = runBlocking {
        val job = Job()
        val repo = UserPreferencesRepository(createPreferencesDataStore(file.toString(), CoroutineScope(Dispatchers.IO + job)))
        assertEquals("", repo.userPreferencesFlow.first().nickname)
        assertEquals(ServerConfig.DEFAULT_HOST, repo.serverConfigFlow.first().host)
        job.cancelAndJoin()
    }
}
