package com.jfaf.irc.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.jfaf.irc.ui.media.VideoPlayer
import com.jfaf.irc.ui.theme.IRCAppTheme
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El reproductor de vídeo de escritorio ante lo que no puede reproducir: debe caer al botón que abre el enlace en el
 * navegador, que reproduce casi todo. Los casos con `.mp4` pasan por el motor nativo del sistema (Media Foundation en
 * Windows, GStreamer en Linux, AVPlayer en macOS); la reproducción de un vídeo real se comprueba a mano.
 */
@OptIn(ExperimentalTestApi::class)
class VideoPlayerUiTest {

    private lateinit var server: HttpServer

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/basura.mp4") { exchange ->
                val body = "esto no es un vídeo".toByteArray()
                exchange.responseHeaders.add("Content-Type", "video/mp4")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/no-existe.mp4") { exchange ->
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

    private fun SkikoComposeUiTest.browserButtons() = onAllNodesWithText("navegador", substring = true).fetchSemanticsNodes().size

    private fun SkikoComposeUiTest.show(videoUrl: String, isPreview: Boolean = false) = setContent {
        IRCAppTheme { VideoPlayer(videoUrl = videoUrl, modifier = Modifier.size(400.dp, 300.dp), isPreview = isPreview) }
    }

    @Test
    fun unFormatoQueNingunMotorReproduceMuestraElBotonDelNavegadorEnseguida() = runSkikoComposeUiTest(size = Size(500f, 400f)) {
        show("https://example.com/clip.xyz")
        waitForIdle()
        assertEquals(1, browserButtons())
    }

    @Test
    fun unArchivoQueNoEsUnVideoCaeAlNavegador() = runSkikoComposeUiTest(size = Size(500f, 400f)) {
        show(url("/basura.mp4"))
        waitUntil(timeoutMillis = 20_000) { browserButtons() > 0 }
    }

    @Test
    fun unEnlaceQueDa404CaeAlNavegador() = runSkikoComposeUiTest(size = Size(500f, 400f)) {
        show(url("/no-existe.mp4"))
        waitUntil(timeoutMillis = 20_000) { browserButtons() > 0 }
    }

    @Test
    fun tambienEnLaVistaPreviaCaeAlNavegador() = runSkikoComposeUiTest(size = Size(500f, 400f)) {
        show(url("/basura.mp4"), isPreview = true)
        waitUntil(timeoutMillis = 20_000) { browserButtons() > 0 }
    }
}
