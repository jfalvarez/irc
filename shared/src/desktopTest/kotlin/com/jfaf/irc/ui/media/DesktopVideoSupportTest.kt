package com.jfaf.irc.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopVideoSupportTest {

    private val windows = "Windows 11"
    private val mac = "Mac OS X"
    private val linux = "Linux"

    private fun canPlay(url: String, os: String) = DesktopVideoSupport.canPlayInApp(url, os)

    @Test
    fun mp4SeReproduceDentroDeLaAppEnLosTresSistemas() {
        listOf(windows, mac, linux).forEach { os ->
            assertTrue(canPlay("https://x.org/clip.mp4", os), "mp4 en $os")
        }
    }

    @Test
    fun webmSoloSeReproduceEnLinux_enWindowsYMacSeAbreEnElNavegador() {
        assertTrue(canPlay("https://x.org/clip.webm", linux))
        assertFalse(canPlay("https://x.org/clip.webm", windows))
        assertFalse(canPlay("https://x.org/clip.webm", mac))
    }

    @Test
    fun elSistemaSeReconoceSinImportarMayusculas() {
        assertTrue(canPlay("https://x.org/clip.webm", "LINUX"))
        assertTrue(canPlay("https://x.org/clip.webm", "linux"))
    }

    @Test
    fun laExtensionIgnoraConsultaFragmentoYMayusculas() {
        assertTrue(canPlay("https://x.org/clip.MP4", windows))
        assertTrue(canPlay("https://x.org/clip.mp4?token=a.b.c", windows))
        assertTrue(canPlay("https://x.org/clip.mp4#t=10", windows))
        assertFalse(canPlay("https://x.org/clip.png?formato=.mp4", windows), "La consulta no cuenta como extensión")
    }

    @Test
    fun loDesconocidoSeAbreEnElNavegadorEnTodos() {
        listOf(windows, mac, linux).forEach { os ->
            assertFalse(canPlay("https://x.org/clip.xyz", os))
            assertFalse(canPlay("https://x.org/sinextension", os))
            assertFalse(canPlay("https://x.org/", os))
            assertFalse(canPlay("https://x.org/clip.mp4.txt", os))
        }
    }

    @Test
    fun extensionOf_noSeDejaEngañarPorPuntosEnElHostOEnRutas() {
        assertEquals("mp4", DesktopVideoSupport.extensionOf("https://cdn.v1.x.org/a.b/clip.MP4?x=1"))
        assertEquals("", DesktopVideoSupport.extensionOf("https://cdn.v1.x.org/ruta.con.puntos/video"))
        assertEquals("", DesktopVideoSupport.extensionOf("https://cdn.x.org"))
    }
}
