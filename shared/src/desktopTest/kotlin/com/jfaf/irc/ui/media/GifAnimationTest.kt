package com.jfaf.irc.ui.media

import androidx.compose.ui.graphics.ImageBitmap
import com.jfaf.irc.ui.media.TestGifs.BLUE
import com.jfaf.irc.ui.media.TestGifs.GREEN
import com.jfaf.irc.ui.media.TestGifs.RED
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GifAnimationTest {

    private fun ImageBitmap.rgbAt(x: Int, y: Int): Int {
        val pixel = IntArray(1)
        readPixels(pixel, startX = x, startY = y, width = 1, height = 1)
        return pixel[0] and 0xFFFFFF
    }

    /** El color principal de un píxel (la paleta del GIF puede variar unos puntos): rojo, verde o azul. */
    private fun colorName(rgb: Int): String {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return when {
            r > 200 && g < 60 && b < 60 -> "rojo"
            g > 200 && r < 60 && b < 60 -> "verde"
            b > 200 && r < 60 && g < 60 -> "azul"
            else -> "otro(%06X)".format(rgb)
        }
    }

    private fun GifAnimation.centerColors(count: Int): List<String> =
        List(count) { colorName(next()!!.image.rgbAt(4, 4)) }

    @Test
    fun unGifSinFinRecorreLosFotogramasEnOrdenYVuelveAlPrimero() {
        GifAnimation.decode(TestGifs.solidFrames(listOf(RED, GREEN, BLUE), loopCount = 0))!!.use { animation ->
            assertEquals(3, animation.frameCount)
            assertEquals(
                listOf("rojo", "verde", "azul", "rojo", "verde", "azul", "rojo"),
                animation.centerColors(7)
            )
        }
    }

    @Test
    fun cadaFotogramaDuraLoQueDeclara_yLosMuyRapidosVanA100ms() {
        GifAnimation.decode(TestGifs.solidFrames(listOf(RED, GREEN), delayCs = 7))!!.use {
            assertEquals(70L, it.next()!!.delayMs) // 7 centésimas
        }
        GifAnimation.decode(TestGifs.solidFrames(listOf(RED, GREEN), delayCs = 1))!!.use {
            assertEquals(100L, it.next()!!.delayMs) // 1 centésima = 10 ms: como en los navegadores
        }
    }

    @Test
    fun sinExtensionDeBucleSeReproduceUnaSolaVezYSeQuedaEnElUltimo() {
        GifAnimation.decode(TestGifs.solidFrames(listOf(RED, GREEN, BLUE), loopCount = null))!!.use { animation ->
            assertEquals(listOf("rojo", "verde", "azul"), animation.centerColors(3))
            assertNull(animation.next())
            assertNull(animation.next(), "Una vez terminado no debe reanudarse")
        }
    }

    @Test
    fun conDosRepeticionesHaceTresPasadasEnTotal() {
        GifAnimation.decode(TestGifs.solidFrames(listOf(RED, GREEN, BLUE), loopCount = 2))!!.use { animation ->
            repeat(9) { assertNotNull(animation.next(), "Fotograma ${it + 1} de 9") }
            assertNull(animation.next())
        }
    }

    @Test
    fun unGifDeUnSoloFotogramaSeMuestraYTermina() {
        GifAnimation.decode(TestGifs.solidFrames(listOf(GREEN), loopCount = 0))!!.use { animation ->
            assertEquals(1, animation.frameCount)
            assertEquals("verde", colorName(animation.next()!!.image.rgbAt(4, 4)))
            assertNull(animation.next(), "No debe quedarse repitiendo un único fotograma")
        }
    }

    @Test
    fun losFotogramasParcialesSeComponenConElAnteriorYCoincidenConLaDecodificacionDeSkia() {
        val gif = TestGifs.partialFrames()
        // Premisa del test: el segundo fotograma depende del primero
        val reference = Codec.makeFromData(Data.makeFromBytes(gif))
        // Skia analiza el GIF de forma perezosa: hay que contar los fotogramas antes de pedir la info de uno
        // (si no, Skiko lee memoria sin inicializar y lanza una excepción).
        assertEquals(2, reference.frameCount)
        assertEquals(0, reference.getFrameInfo(1).requiredFrame, "El fotograma 2 debe apoyarse en el 1")
        val expected = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(8, 8)) }
        reference.readPixels(expected, 1) // Skia reconstruye el fotograma por su cuenta

        GifAnimation.decode(gif)!!.use { animation ->
            animation.next() // fotograma 1
            val second = animation.next()!!.image
            assertEquals("rojo", colorName(second.rgbAt(0, 0)), "Fuera del cuadrado se conserva el fondo del primero")
            assertEquals("verde", colorName(second.rgbAt(4, 4)), "Dentro del cuadrado va el segundo")
            for ((x, y) in listOf(0 to 0, 7 to 7, 4 to 4, 2 to 2, 5 to 5, 1 to 6)) {
                assertEquals(expected.getColor(x, y) and 0xFFFFFF, second.rgbAt(x, y), "Píxel ($x, $y)")
            }
        }
        reference.close()
        expected.close()
    }

    @Test
    fun alVolverAlPrimerFotogramaNoQuedanRestosDelUltimo() {
        GifAnimation.decode(TestGifs.partialFrames())!!.use { animation ->
            animation.next()
            animation.next() // el cuadrado verde
            val again = animation.next()!!.image // vuelta al primero (rojo liso)
            assertEquals("rojo", colorName(again.rgbAt(4, 4)), "El cuadrado verde del fotograma 2 no debe quedar")
        }
    }

    @Test
    fun loQueNoEsUnaImagenNoSeDecodifica() {
        assertNull(GifAnimation.decode("esto no es un GIF".toByteArray()))
        assertNull(GifAnimation.decode(ByteArray(0)))
    }
}
