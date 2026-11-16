package com.jfaf.irc.domain.linkpreview

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

@OptIn(ExperimentalTime::class)
class LinkPreviewRepositoryTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() = scope.cancel()

    private fun html(title: String) = "<head><meta property=\"og:title\" content=\"$title\"></head>"

    private class FakeFetcher(var respond: (String) -> FetchedPage? = { FetchedPage(it, "<head><title>T</title></head>") }) : LinkPreviewFetcher {
        val calls = mutableListOf<String>()
        var active = 0
        var maxActive = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun fetch(url: String): FetchedPage? {
            calls += url
            active++
            maxActive = maxOf(maxActive, active)
            try {
                gate?.await()
                return respond(url)
            } finally {
                active--
            }
        }
    }

    @Test
    fun noPideLoQueNoEsUnaWebPublica() = runBlocking {
        val fetcher = FakeFetcher()
        val repo = LinkPreviewRepository(fetcher, scope)
        assertNull(repo.preview("http://localhost/x"))
        assertNull(repo.preview("http://192.168.1.1/"))
        assertNull(repo.preview("ftp://example.com/x"))
        assertTrue(fetcher.calls.isEmpty(), "Ni siquiera se debe intentar: ${fetcher.calls}")
    }

    @Test
    fun laMismaUrlSePideUnaVez() = runBlocking {
        val fetcher = FakeFetcher()
        val repo = LinkPreviewRepository(fetcher, scope)
        val first = repo.preview("https://example.com/a")
        val second = repo.preview("https://example.com/a#otra-parte")
        assertEquals("T", first?.title)
        assertEquals(first, second)
        assertEquals(1, fetcher.calls.size)
    }

    @Test
    fun peticionesALaVezDeLaMismaUrlComparteLaDescarga() = runBlocking {
        val fetcher = FakeFetcher().also { it.gate = CompletableDeferred() }
        val repo = LinkPreviewRepository(fetcher, scope)
        val a = async { repo.preview("https://example.com/a") }
        val b = async { repo.preview("https://example.com/a") }
        delay(100)
        fetcher.gate!!.complete(Unit)
        val results = listOf(a, b).awaitAll()
        assertEquals(1, fetcher.calls.size)
        assertNotNull(results[0])
        assertEquals(results[0], results[1])
    }

    @Test
    fun siQuienPidioSeVa_ladescargaSigueYQuedaEnCache() = runBlocking {
        val fetcher = FakeFetcher().also { it.gate = CompletableDeferred() }
        val repo = LinkPreviewRepository(fetcher, scope)
        val caller = async { repo.preview("https://example.com/a") }
        delay(100)
        caller.cancel() // el mensaje salió de la lista al hacer scroll
        fetcher.gate!!.complete(Unit)
        delay(200)
        assertEquals("T", repo.preview("https://example.com/a")?.title)
        assertEquals(1, fetcher.calls.size)
    }

    @Test
    fun limitaLasDescargasALaVez() = runBlocking {
        val fetcher = FakeFetcher().also { it.gate = CompletableDeferred() }
        val repo = LinkPreviewRepository(fetcher, scope, maxConcurrent = 2)
        val jobs = (1..6).map { async { repo.preview("https://example.com/$it") } }
        delay(200)
        assertEquals(2, fetcher.maxActive)
        fetcher.gate!!.complete(Unit)
        jobs.awaitAll()
        assertEquals(6, fetcher.calls.size)
        assertTrue(fetcher.maxActive <= 2)
    }

    @Test
    fun loQueFallaSeRecuerdaUnRato_yLuegoSeReintenta() = runBlocking {
        val clock = TestTimeSource()
        val fetcher = FakeFetcher { null }
        val repo = LinkPreviewRepository(fetcher, scope, failureMemory = 2.minutes, timeSource = clock)
        assertNull(repo.preview("https://example.com/a"))
        assertNull(repo.preview("https://example.com/a"))
        assertEquals(1, fetcher.calls.size, "El fallo reciente no se reintenta")

        clock += 3.minutes
        fetcher.respond = { FetchedPage(it, html("Ya funciona")) }
        assertEquals("Ya funciona", repo.preview("https://example.com/a")?.title)
        assertEquals(2, fetcher.calls.size)
    }

    @Test
    fun loQueSeConsiguioNoCaduca() = runBlocking {
        val clock = TestTimeSource()
        val fetcher = FakeFetcher()
        val repo = LinkPreviewRepository(fetcher, scope, failureMemory = 2.minutes, timeSource = clock)
        repo.preview("https://example.com/a")
        clock += 60.minutes
        repo.preview("https://example.com/a")
        assertEquals(1, fetcher.calls.size)
    }

    @Test
    fun unaPaginaSinDatos_noDaVistaPrevia() = runBlocking {
        val repo = LinkPreviewRepository(FakeFetcher { FetchedPage(it, "<head></head><body>hola</body>") }, scope)
        assertNull(repo.preview("https://example.com/a"))
    }

    @Test
    fun siElDescargadorLanzaUnaExcepcion_noSePropaga() = runBlocking {
        val repo = LinkPreviewRepository(FakeFetcher { throw IllegalStateException("boom") }, scope)
        assertNull(repo.preview("https://example.com/a"))
    }

    @Test
    fun laCacheTieneUnMaximo_yTiraLoMasAntiguo() = runBlocking {
        val fetcher = FakeFetcher()
        val repo = LinkPreviewRepository(fetcher, scope, maxEntries = 2)
        repo.preview("https://example.com/1")
        repo.preview("https://example.com/2")
        repo.preview("https://example.com/3") // saca la 1
        assertEquals(3, fetcher.calls.size)
        repo.preview("https://example.com/2")
        repo.preview("https://example.com/3")
        assertEquals(3, fetcher.calls.size, "2 y 3 siguen en cache")
        repo.preview("https://example.com/1")
        assertEquals(4, fetcher.calls.size, "1 se tiró y se vuelve a pedir")
    }
}
