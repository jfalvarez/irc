package com.jfaf.irc.domain.linkpreview

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Las vistas previas de enlaces con memoria (UI-31): cada URL se pide una sola vez aunque aparezca en varios mensajes
 * o el usuario suba y baje por el chat; las peticiones a la vez están limitadas, y las que fallan se recuerdan un rato
 * para no insistir. Todo en memoria: al cerrar la app no queda nada. La petición sigue aunque la pantalla que la pidió
 * salga de la lista (así no se pierde el trabajo al hacer scroll).
 */
class LinkPreviewRepository(
    private val fetcher: LinkPreviewFetcher,
    private val scope: CoroutineScope,
    private val maxEntries: Int = 200,
    maxConcurrent: Int = 3,
    private val failureMemory: Duration = 2.minutes,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {
    private class Entry(val preview: LinkPreview?, val storedAt: TimeMark)

    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, Entry>()
    private val inFlight = HashMap<String, Deferred<LinkPreview?>>()
    private val permits = Semaphore(maxConcurrent)

    /** La vista previa de [url], o null si no hay (no es una web pública, falló o no dice nada). */
    suspend fun preview(url: String): LinkPreview? {
        if (!UrlSafety.isPublicHttpUrl(url)) return null
        val key = url.substringBefore('#')

        val job = mutex.withLock {
            cache[key]?.let { entry ->
                val stale = entry.preview == null && entry.storedAt.elapsedNow() > failureMemory
                if (!stale) {
                    cache.remove(key)
                    cache[key] = entry // el más reciente va al final
                    return entry.preview
                }
                cache.remove(key)
            }
            inFlight.getOrPut(key) { scope.async { permits.withPermit { load(key) } } }
        }
        return job.await()
    }

    private suspend fun load(key: String): LinkPreview? {
        val preview = try {
            fetcher.fetch(key)?.let { LinkPreviewParser.parse(it.html, it.finalUrl, requestedUrl = key) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            mutex.withLock { inFlight.remove(key) }
            throw e
        } catch (_: Exception) {
            null
        }
        mutex.withLock {
            cache[key] = Entry(preview, timeSource.markNow())
            while (cache.size > maxEntries) cache.remove(cache.keys.first())
            inFlight.remove(key)
        }
        return preview
    }
}
