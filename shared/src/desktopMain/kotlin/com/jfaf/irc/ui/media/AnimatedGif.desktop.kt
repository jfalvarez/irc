package com.jfaf.irc.ui.media

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.jfaf.irc.log.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Coil 3 no anima GIF en escritorio: los anima la app (descarga + decodificación con Skia). */
actual val platformAnimatesGifs: Boolean = true

@Composable
actual fun AnimatedGif(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    onResult: (animated: Boolean) -> Unit
) {
    var frame by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    val latestOnResult by rememberUpdatedState(onResult)

    // Se cancela solo al salir de la composición (p. ej. al desplazar el mensaje fuera de la lista).
    LaunchedEffect(url) {
        val bytes = GifSource.bytes(url)
        val animation = bytes?.let { withContext(Dispatchers.Default) { GifAnimation.decode(it) } }
        if (animation == null) {
            latestOnResult(false)
            return@LaunchedEffect
        }
        var reported = false
        try {
            while (true) {
                val next = withContext(Dispatchers.Default) { animation.next() } ?: break
                frame = next.image
                if (!reported) {
                    reported = true
                    latestOnResult(true)
                }
                delay(next.delayMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // GIF corrupto o truncado a mitad: se queda el último fotograma, y si ni el primero salió, el cargador normal
            Log.d("AnimatedGif", "Se interrumpió la animación de $url: ${e.message}")
            if (!reported) latestOnResult(false)
        } finally {
            animation.close()
        }
    }

    frame?.let { Image(bitmap = it, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale) }
}

/** Descarga los GIF con el mismo cliente OkHttp que usa Coil y guarda los bytes en memoria. */
internal object GifSource {
    private const val TAG = "GifSource"
    private const val MAX_BYTES = 20 * 1024 * 1024 // un GIF mayor se deja al cargador normal
    private const val CACHE_BYTES = 48L * 1024 * 1024

    private val client by lazy { OkHttpClient() }
    private val cache = ByteLruCache(CACHE_BYTES)

    /** Los bytes del GIF de [url] (de la caché si ya se bajó), o `null` si no se pudo descargar. */
    suspend fun bytes(url: String): ByteArray? {
        cache.get(url)?.let { return it }
        val downloaded = withContext(Dispatchers.IO) { download(url) } ?: return null
        cache.put(url, downloaded)
        return downloaded
    }

    private fun download(url: String): ByteArray? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body
            when {
                !response.isSuccessful || body == null -> null
                body.contentLength() > MAX_BYTES -> null
                else -> body.byteStream().readNBytes(MAX_BYTES + 1).takeIf { it.size <= MAX_BYTES }
            }
        }
    } catch (e: Exception) {
        // Red caída, URL inválida, certificado… Se usará el cargador normal.
        Log.d(TAG, "No se pudo descargar el GIF: ${e.message}")
        null
    }
}
