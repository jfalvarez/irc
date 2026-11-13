package com.jfaf.irc.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.desktop.GifFixtures.BLUE
import com.jfaf.irc.desktop.GifFixtures.GREEN
import com.jfaf.irc.desktop.GifFixtures.RED
import com.jfaf.irc.ui.media.ImageLoadState
import com.jfaf.irc.ui.media.MediaImage
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Los GIF animados en escritorio de punta a punta: la UI real (`MediaImage`) pide el GIF a un servidor HTTP local
 * (el mismo camino de red que en producción) y se comprueba lo que se pinta.
 */
@OptIn(ExperimentalTestApi::class)
class AnimatedGifUiTest {

    private lateinit var server: HttpServer
    private val gif = GifFixtures.solidFrames(listOf(RED, GREEN, BLUE), delayCs = 10)
    private val gifRequests = AtomicInteger()

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/a.gif") { exchange ->
                gifRequests.incrementAndGet()
                exchange.responseHeaders.add("Content-Type", "image/gif")
                exchange.sendResponseHeaders(200, gif.size.toLong())
                exchange.responseBody.use { it.write(gif) }
            }
            createContext("/roto.gif") { exchange ->
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
            start()
        }
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    /** El color que se ve en el centro de la ventana (el GIF es liso, así que basta un píxel). */
    private fun SkikoComposeUiTest.centerColor(): String {
        val image = onRoot().captureToImage()
        val pixel = IntArray(1)
        image.readPixels(pixel, startX = image.width / 2, startY = image.height / 2, width = 1, height = 1)
        val r = (pixel[0] shr 16) and 0xFF
        val g = (pixel[0] shr 8) and 0xFF
        val b = pixel[0] and 0xFF
        return when {
            r > 200 && g < 60 && b < 60 -> "rojo"
            g > 200 && r < 60 && b < 60 -> "verde"
            b > 200 && r < 60 && g < 60 -> "azul"
            else -> "otro"
        }
    }

    @Test
    fun unGifSeAnimaYPasaPorTodosSusFotogramas() = runSkikoComposeUiTest(size = Size(200f, 200f)) {
        var state by mutableStateOf(ImageLoadState.Loading)
        setContent {
            MediaImage(url = url("/a.gif"), contentDescription = "gif", modifier = Modifier.fillMaxSize(), onState = { state = it })
        }

        waitUntil(timeoutMillis = 10_000) { state == ImageLoadState.Success }

        val seen = linkedSetOf<String>()
        waitUntil(timeoutMillis = 15_000) {
            seen += centerColor()
            seen.containsAll(listOf("rojo", "verde", "azul"))
        }
        assertTrue(seen.containsAll(listOf("rojo", "verde", "azul")), "Colores vistos: $seen")
    }

    @Test
    fun unGifQueNoSePuedeDescargarCaeAlCargadorNormalYAcabaEnError() = runSkikoComposeUiTest(size = Size(200f, 200f)) {
        var state by mutableStateOf(ImageLoadState.Loading)
        setContent {
            MediaImage(url = url("/roto.gif"), contentDescription = "gif", modifier = Modifier.fillMaxSize(), onState = { state = it })
        }

        waitUntil(timeoutMillis = 15_000) { state == ImageLoadState.Error }
    }

    @Test
    fun elMismoGifSeDescargaUnaSolaVezAunqueSeVuelvaAPintar() = runSkikoComposeUiTest(size = Size(200f, 200f)) {
        var show by mutableStateOf(true)
        var state by mutableStateOf(ImageLoadState.Loading)
        setContent {
            if (show) {
                MediaImage(url = url("/a.gif"), contentDescription = "gif", modifier = Modifier.fillMaxSize(), onState = { state = it })
            }
        }
        waitUntil(timeoutMillis = 10_000) { state == ImageLoadState.Success }

        // Sale de pantalla (p. ej. se desplaza la lista) y vuelve a entrar
        show = false
        waitForIdle()
        state = ImageLoadState.Loading
        show = true
        waitUntil(timeoutMillis = 10_000) { state == ImageLoadState.Success }

        assertEquals(1, gifRequests.get(), "La segunda vez debe salir de la caché")
    }
}
