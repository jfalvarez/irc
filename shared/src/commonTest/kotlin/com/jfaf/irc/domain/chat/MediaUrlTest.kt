package com.jfaf.irc.domain.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaUrlTest {

    @Test
    fun reconoceLosGifPorLaExtension() {
        assertTrue(MediaUrl.isGif("https://media.giphy.com/media/abc/giphy.gif"))
        assertTrue(MediaUrl.isGif("https://x.org/a.GIF"))
        assertTrue(MediaUrl.isGif("http://x.org/ruta/con.puntos/a.gif"))
    }

    @Test
    fun ignoraLaConsultaYElFragmento() {
        assertTrue(MediaUrl.isGif("https://x.org/a.gif?width=200&cid=1"))
        assertTrue(MediaUrl.isGif("https://x.org/a.gif#detalle"))
        assertFalse(MediaUrl.isGif("https://x.org/a.png?formato=.gif"))
        assertFalse(MediaUrl.isGif("https://x.org/a.png#.gif"))
    }

    @Test
    fun noConfundeOtrosFormatosNiExtensionesParecidas() {
        assertFalse(MediaUrl.isGif("https://x.org/a.png"))
        assertFalse(MediaUrl.isGif("https://x.org/a.webp"))
        assertFalse(MediaUrl.isGif("https://x.org/a.gifv"))
        assertFalse(MediaUrl.isGif("https://x.org/gif"))
        assertFalse(MediaUrl.isGif("https://x.org/"))
        assertFalse(MediaUrl.isGif(""))
    }
}
