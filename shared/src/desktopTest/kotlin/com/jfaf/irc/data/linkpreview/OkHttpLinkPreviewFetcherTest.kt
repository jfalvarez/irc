package com.jfaf.irc.data.linkpreview

import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El descargador de vistas previas contra un servidor HTTP mínimo local (y la guarda de DNS sin red). */
class OkHttpLinkPreviewFetcherTest {

    private class Reply(val status: String = "200 OK", val headers: List<String> = listOf("Content-Type: text/html; charset=utf-8"), val body: ByteArray = ByteArray(0))

    private val hits = mutableListOf<String>()
    private val routes = mutableMapOf<String, Reply>()
    private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
    private val base get() = "http://127.0.0.1:${server.localPort}"
    @Volatile private var running = true

    init {
        thread(isDaemon = true) {
            while (running) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) { serve(socket) }
            }
        }
    }

    private fun serve(socket: Socket) = socket.use {
        val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val path = input.readLine()?.split(' ')?.getOrNull(1) ?: return
        while (input.readLine().orEmpty().isNotEmpty()) { /* cabeceras */ }
        synchronized(hits) { hits += path }
        val reply = routes[path] ?: Reply("404 Not Found")
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 ${reply.status}\r\n".toByteArray())
        reply.headers.forEach { out.write("$it\r\n".toByteArray()) }
        out.write("Content-Length: ${reply.body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        try { out.write(reply.body); out.flush() } catch (_: Exception) { /* el cliente cortó */ }
    }

    @AfterTest
    fun tearDown() {
        running = false
        server.close()
    }

    private fun html(text: String) = Reply(body = text.toByteArray())
    private val fetcher = OkHttpLinkPreviewFetcher(publicOnly = false)

    @Test
    fun descargaElHtml() = runBlocking {
        routes["/a"] = html("<head><title>Hola</title></head>")
        val page = assertNotNull(fetcher.fetch("$base/a"))
        assertTrue("<title>Hola</title>" in page.html)
        assertEquals("$base/a", page.finalUrl)
    }

    @Test
    fun loQueNoEsHtml_oNoEsUn200_dadaNull() = runBlocking {
        routes["/img"] = Reply(headers = listOf("Content-Type: image/png"), body = byteArrayOf(1, 2, 3))
        routes["/json"] = Reply(headers = listOf("Content-Type: application/json"), body = "{}".toByteArray())
        routes["/err"] = Reply(status = "500 Internal Server Error", body = "<title>x</title>".toByteArray())
        assertNull(fetcher.fetch("$base/img"))
        assertNull(fetcher.fetch("$base/json"))
        assertNull(fetcher.fetch("$base/err"))
        assertNull(fetcher.fetch("$base/no-existe"))
    }

    @Test
    fun sigueRedireccionesYDaLaUrlFinal() = runBlocking {
        routes["/corto"] = Reply("302 Found", listOf("Location: /medio"))
        routes["/medio"] = Reply("301 Moved Permanently", listOf("Location: $base/final"))
        routes["/final"] = html("<title>Final</title>")
        val page = assertNotNull(fetcher.fetch("$base/corto"))
        assertEquals("$base/final", page.finalUrl)
    }

    @Test
    fun tantasRedireccionesNo() = runBlocking {
        routes["/a"] = Reply("302 Found", listOf("Location: /b"))
        routes["/b"] = Reply("302 Found", listOf("Location: /c"))
        routes["/c"] = Reply("302 Found", listOf("Location: /d"))
        routes["/d"] = Reply("302 Found", listOf("Location: /e"))
        routes["/e"] = html("<title>Lejos</title>")
        assertNull(fetcher.fetch("$base/a"), "Más de ${OkHttpLinkPreviewFetcher.MAX_REDIRECTS} redirecciones")
        routes["/loop"] = Reply("302 Found", listOf("Location: /loop"))
        assertNull(fetcher.fetch("$base/loop"))
    }

    @Test
    fun soloLeeElPrimerMedioMega() = runBlocking {
        val big = "<head><title>Grande</title></head>" + "x".repeat(2_000_000)
        routes["/big"] = html(big)
        val page = assertNotNull(fetcher.fetch("$base/big"))
        assertTrue(page.html.length <= OkHttpLinkPreviewFetcher.MAX_BYTES, "Leyó ${page.html.length} caracteres")
        assertTrue("<title>Grande</title>" in page.html)
    }

    @Test
    fun adivinaElJuegoDeCaracteresSiLaCabeceraNoLoDice() = runBlocking {
        val latin1 = "<head><meta charset=\"iso-8859-1\"><title>Año</title></head>".toByteArray(Charsets.ISO_8859_1)
        routes["/latin"] = Reply(headers = listOf("Content-Type: text/html"), body = latin1)
        assertTrue("Año" in assertNotNull(fetcher.fetch("$base/latin")).html)
    }

    @Test
    fun noEnviaCookiesNiDatosDelUsuario() = runBlocking {
        routes["/a"] = Reply(headers = listOf("Content-Type: text/html", "Set-Cookie: s=1"), body = "<title>x</title>".toByteArray())
        fetcher.fetch("$base/a")
        fetcher.fetch("$base/a")
        assertEquals(2, synchronized(hits) { hits.size })
    }

    // --- Con la guarda puesta (la de verdad): nada de lo local ---
    @Test
    fun conLaGuardaPuesta_unServidorLocalNiSeToca() = runBlocking {
        routes["/a"] = html("<title>secreto</title>")
        assertNull(OkHttpLinkPreviewFetcher().fetch("$base/a"))
        assertNull(OkHttpLinkPreviewFetcher().fetch("http://localhost:${server.localPort}/a"))
        assertTrue(synchronized(hits) { hits.isEmpty() }, "No debe haber llegado ninguna petición: $hits")
    }

    @Test
    fun laGuardaDeDnsRechazaLoNoPublico() {
        fun nonPublic(ip: String) = PublicOnlyDns.isNonPublic(InetAddress.getByName(ip))
        listOf("127.0.0.1", "10.1.2.3", "172.16.5.5", "192.168.0.1", "169.254.169.254", "100.64.0.1", "0.0.0.0", "224.0.0.1", "::1", "fe80::1", "fc00::1", "fd00::1")
            .forEach { assertTrue(nonPublic(it), "$it no es público") }
        listOf("8.8.8.8", "1.1.1.1", "93.184.216.34", "2606:4700:4700::1111").forEach { assertFalse(nonPublic(it), "$it es público") }
    }
}
