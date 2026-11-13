package com.jfaf.irc.data.prefs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesDataStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun path() = tmp.root.resolve("datastore/$USER_PREFERENCES_FILE").absolutePath

    @Test
    fun ajustesSeConservanAlReabrir() = runBlocking {
        val job1 = Job()
        val first = UserPreferencesRepository(createPreferencesDataStore(path(), CoroutineScope(Dispatchers.IO + job1)))
        first.updateNickname("ana")
        first.updateServerConfig(ServerConfig("irc.example.org", 6668, 6698))
        job1.cancelAndJoin() // como al cerrar la app: sólo un DataStore activo por archivo

        assertTrue(tmp.root.resolve("datastore/$USER_PREFERENCES_FILE").isFile)

        val job2 = Job()
        val second = UserPreferencesRepository(createPreferencesDataStore(path(), CoroutineScope(Dispatchers.IO + job2)))
        assertEquals("ana", second.userPreferencesFlow.first().nickname)
        assertEquals(ServerConfig("irc.example.org", 6668, 6698), second.serverConfigFlow.first())
        job2.cancelAndJoin()
    }

    @Test
    fun sinArchivo_valoresPorDefecto() = runBlocking {
        val job = Job()
        val repo = UserPreferencesRepository(createPreferencesDataStore(path(), CoroutineScope(Dispatchers.IO + job)))
        assertEquals("", repo.userPreferencesFlow.first().nickname)
        assertEquals(ServerConfig.DEFAULT_HOST, repo.serverConfigFlow.first().host)
        job.cancelAndJoin()
    }
}
