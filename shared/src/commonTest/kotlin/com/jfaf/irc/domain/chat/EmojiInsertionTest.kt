package com.jfaf.irc.domain.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class EmojiInsertionTest {

    private val smile = "😀" // 😀: ocupa dos unidades (par sustituto), como casi todos

    @Test
    fun seInsertaEnLaPosicionDelCursorYElCursorQuedaTrasElEmoticono() {
        val result = EmojiInsertion.insert("holamundo", 4, 4, smile)
        assertEquals("hola${smile}mundo", result.text)
        assertEquals(4 + smile.length, result.cursor)
    }

    @Test
    fun alPrincipioYAlFinal() {
        assertEquals("${smile}hola", EmojiInsertion.insert("hola", 0, 0, smile).text)
        val end = EmojiInsertion.insert("hola", 4, 4, smile)
        assertEquals("hola$smile", end.text)
        assertEquals(end.text.length, end.cursor)
    }

    @Test
    fun conTextoSeleccionadoLoSustituye() {
        val result = EmojiInsertion.insert("hola mundo", 5, 10, smile)
        assertEquals("hola $smile", result.text)
        assertEquals(5 + smile.length, result.cursor)
    }

    @Test
    fun laSeleccionPuedeVenirAlReves() {
        assertEquals("hola $smile", EmojiInsertion.insert("hola mundo", 10, 5, smile).text)
    }

    @Test
    fun conPosicionesFueraDelTextoSeAcotan() {
        assertEquals("hola$smile", EmojiInsertion.insert("hola", 99, 99, smile).text)
        assertEquals("${smile}hola", EmojiInsertion.insert("hola", -3, -3, smile).text)
    }

    @Test
    fun enUnCampoVacio() {
        val result = EmojiInsertion.insert("", 0, 0, smile)
        assertEquals(smile, result.text)
        assertEquals(smile.length, result.cursor)
    }

    @Test
    fun noPartePorLaMitadUnEmoticonoYaPresente() {
        // Con el cursor justo después de un 😀 ya escrito se inserta otro a continuación, sin romper el par sustituto
        val result = EmojiInsertion.insert(smile, smile.length, smile.length, "🔥") // 🔥
        assertEquals(smile + "🔥", result.text)
    }
}

class RecentEmojisTest {

    @Test
    fun elUltimoUsadoVaPrimeroYSinRepetir() {
        assertEquals(listOf("b", "a"), RecentEmojis.add(listOf("a"), "b"))
        assertEquals(listOf("a", "b", "c"), RecentEmojis.add(listOf("b", "a", "c"), "a"))
    }

    @Test
    fun noPasaDelMaximo() {
        val many = (1..RecentEmojis.MAX).map { "e$it" }
        val result = RecentEmojis.add(many, "nuevo")
        assertEquals(RecentEmojis.MAX, result.size)
        assertEquals("nuevo", result.first())
        assertEquals("e${RecentEmojis.MAX - 1}", result.last(), "Sale el más antiguo")
    }

    @Test
    fun seGuardanYSeLeenSinPerderNada() {
        val recents = listOf("😀", "❤\uFE0F", "🔥")
        assertEquals(recents, RecentEmojis.decode(RecentEmojis.encode(recents)))
    }

    @Test
    fun lecturaTolerante() {
        assertEquals(emptyList(), RecentEmojis.decode(null))
        assertEquals(emptyList(), RecentEmojis.decode(""))
        assertEquals(listOf("a", "b"), RecentEmojis.decode("  a   b a "), "Sin vacíos ni repetidos")
    }
}
