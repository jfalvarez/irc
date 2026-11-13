package com.jfaf.irc.ui.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Un GIF decodificado fotograma a fotograma con Skia (Coil 3 no anima GIF en escritorio). Sólo hay un fotograma en
 * memoria a la vez, así que un GIF largo no ocupa lo que todos sus fotogramas juntos.
 * No es seguro entre hilos: se usa desde una sola corrutina, de un fotograma al siguiente.
 */
internal class GifAnimation private constructor(
    private val codec: Codec,
    private val bitmap: Bitmap
) : AutoCloseable {

    /** Un fotograma ya compuesto con los anteriores, y cuánto debe verse. */
    class Frame(val image: ImageBitmap, val delayMs: Long)

    // Debe leerse antes que cualquier getFrameInfo: Skia analiza el GIF de forma perezosa y sin haber contado los
    // fotogramas, Skiko lee memoria sin inicializar al pedir la info de uno (y lanza una excepción).
    val frameCount: Int = codec.frameCount

    private val plays: Int? = gifTotalPlays(codec.repetitionCount) // null = sin fin
    private var nextIndex = 0
    private var completedPasses = 0
    private var lastDecoded = -1

    /**
     * El siguiente fotograma, volviendo al primero en cada repetición, o `null` cuando ya se hicieron todas las
     * repeticiones del GIF (o sólo tiene un fotograma): entonces se queda a la vista el último.
     */
    fun next(): Frame? {
        if (nextIndex >= frameCount) {
            completedPasses++
            if (frameCount <= 1 || (plays != null && completedPasses >= plays)) return null
            nextIndex = 0
        }
        val index = nextIndex++
        // Si este fotograma se apoya en el anterior y el bitmap aún lo contiene, Skia sólo pinta lo que cambia;
        // si no, reconstruye lo que necesite desde el fotograma que corresponda.
        val prior = if (index > 0 && lastDecoded == index - 1 && codec.getFrameInfo(index).requiredFrame == index - 1) index - 1 else -1
        codec.readPixels(bitmap, index, prior)
        lastDecoded = index
        // Se copia: el bitmap se reutiliza para el fotograma siguiente.
        val image = Image.makeFromBitmap(bitmap).toComposeImageBitmap()
        return Frame(image, gifFrameDelayMs(codec.getFrameInfo(index).duration))
    }

    override fun close() {
        codec.close()
        bitmap.close()
    }

    companion object {
        /** Tope de píxeles por fotograma (unos 32 MB): un GIF mayor se deja al cargador normal, que lo pinta fijo. */
        private const val MAX_PIXELS = 8_000_000L

        /** Decodifica [bytes] como GIF (o cualquier imagen que Skia lea), o `null` si no son una imagen válida. */
        fun decode(bytes: ByteArray): GifAnimation? {
            val codec = try {
                Codec.makeFromData(Data.makeFromBytes(bytes))
            } catch (e: Exception) {
                return null // no es una imagen que Skia entienda
            }
            val width = codec.width
            val height = codec.height
            if (width <= 0 || height <= 0 || codec.frameCount <= 0 || width.toLong() * height > MAX_PIXELS) {
                codec.close()
                return null
            }
            return try {
                val bitmap = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(width, height)) }
                GifAnimation(codec, bitmap)
            } catch (e: Exception) {
                codec.close()
                null
            }
        }
    }
}
