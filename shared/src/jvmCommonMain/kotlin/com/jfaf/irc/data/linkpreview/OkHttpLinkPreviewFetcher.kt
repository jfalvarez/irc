package com.jfaf.irc.data.linkpreview

import com.jfaf.irc.domain.linkpreview.FetchedPage
import com.jfaf.irc.domain.linkpreview.LinkPreviewFetcher
import com.jfaf.irc.domain.linkpreview.UrlSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Descarga el HTML de una web para su vista previa (UI-31), con OkHttp: Android y escritorio son JVM, así que lo comparten
 * (en otra plataforma se escribiría otra implementación de [LinkPreviewFetcher]). Es la parte arriesgada, porque pide
 * por cuenta del remitente de un mensaje; por eso:
 *  - sólo webs públicas por http(s) ([UrlSafety]), y lo que resuelva el DNS se vuelve a comprobar en cada salto
 *    ([PublicOnlyDns]): un nombre público que resuelve a `127.0.0.1` o a la red local se rechaza;
 *  - redirecciones a mano (máximo [MAX_REDIRECTS]) y cada destino pasa las mismas comprobaciones;
 *  - sin cookies, sin credenciales, un agente genérico y nada del usuario en las cabeceras;
 *  - tiempos cortos, y sólo se lee [MAX_BYTES] de una respuesta `text/html`.
 * [publicOnly] = false lo desactiva todo (sólo para las pruebas, que hablan con un servidor local).
 */
class OkHttpLinkPreviewFetcher(private val publicOnly: Boolean = true) : LinkPreviewFetcher {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(if (publicOnly) PublicOnlyDns else Dns.SYSTEM)
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .retryOnConnectionFailure(false)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    override suspend fun fetch(url: String): FetchedPage? = runInterruptible(Dispatchers.IO) {
        try {
            fetchBlocking(url)
        } catch (_: java.io.IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun fetchBlocking(startUrl: String): FetchedPage? {
        var current = startUrl
        var redirects = 0
        while (true) {
            if (publicOnly && !UrlSafety.isPublicHttpUrl(current)) return null
            val httpUrl = current.toHttpUrlOrNull() ?: return null
            val request = Request.Builder()
                .url(httpUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    if (++redirects > MAX_REDIRECTS) return null
                    val location = response.header("Location") ?: return null
                    current = response.request.url.resolve(location)?.toString() ?: return null
                    return@use
                }
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val type = body.contentType()
                val isHtml = type != null && ((type.type == "text" && type.subtype == "html") || (type.type == "application" && type.subtype == "xhtml+xml"))
                if (!isHtml) return null

                val source = body.source()
                source.request(MAX_BYTES)
                val bytes = source.buffer.readByteArray(minOf(source.buffer.size, MAX_BYTES))
                return FetchedPage(response.request.url.toString(), String(bytes, charsetOf(type?.charset(), bytes)))
            }
        }
    }

    /** El juego de caracteres de la cabecera; si falta, el de `<meta charset=…>` (aún hay webs en ISO-8859-1); si no, UTF-8. */
    private fun charsetOf(declared: Charset?, bytes: ByteArray): Charset {
        if (declared != null) return declared
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val name = CHARSET_META.find(head)?.groupValues?.get(1)
        return runCatching { if (name != null && Charset.isSupported(name)) Charset.forName(name) else null }.getOrNull() ?: Charsets.UTF_8
    }

    companion object {
        const val MAX_BYTES = 512L * 1024
        const val MAX_REDIRECTS = 3
        private const val USER_AGENT = "Mozilla/5.0 (compatible; IRCApp-LinkPreview/1.0)"
        private val CHARSET_META = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
    }
}

/** DNS que rechaza cualquier nombre que resuelva, aunque sea a una sola dirección, a algo que no sea público. */
internal object PublicOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.any(::isNonPublic)) throw UnknownHostException("$hostname resuelve a una dirección no pública")
        return addresses
    }

    fun isNonPublic(address: InetAddress): Boolean = when {
        address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress -> true
        address is Inet4Address -> UrlSafety.isPrivateIpv4(address.address.map { it.toInt() and 0xFF })
        // fc00::/7 (privadas de IPv6); las IPv4 mapeadas ya las convierte Java en Inet4Address
        address is Inet6Address -> (address.address[0].toInt() and 0xFE) == 0xFC
        else -> true
    }
}
