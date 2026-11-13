package com.jfaf.irc.ui.media

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode

/** GIF de prueba generados con `ImageIO`, sin archivos binarios en el repositorio. */
internal object TestGifs {
    const val RED = 0xFF0000
    const val GREEN = 0x00FF00
    const val BLUE = 0x0000FF

    /** Un fotograma: su imagen, dónde se coloca en el lienzo y cuánto dura (en centésimas, como el GIF). */
    class FrameSpec(val image: BufferedImage, val left: Int = 0, val top: Int = 0, val delayCs: Int = 10)

    fun solid(rgb: Int, size: Int = 8): BufferedImage = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).also { img ->
        for (x in 0 until size) for (y in 0 until size) img.setRGB(x, y, 0xFF000000.toInt() or rgb)
    }

    /** Fotogramas lisos a pantalla completa. [loopCount]: 0 = sin fin, n = n repeticiones más, `null` = sin extensión (una vez). */
    fun solidFrames(colors: List<Int>, delayCs: Int = 10, loopCount: Int? = 0): ByteArray =
        write(colors.map { FrameSpec(solid(it), delayCs = delayCs) }, loopCount)

    /** El segundo fotograma sólo cubre un cuadrado de 4x4 y se apoya en el primero (rojo, 8x8). */
    fun partialFrames(): ByteArray = write(
        listOf(
            FrameSpec(solid(RED, 8)),
            FrameSpec(solid(GREEN, 4), left = 2, top = 2)
        ),
        loopCount = 0
    )

    fun write(frames: List<FrameSpec>, loopCount: Int?): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(null)
            frames.forEachIndexed { index, frame ->
                val param = writer.defaultWriteParam
                val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame.image), param)
                val format = "javax_imageio_gif_image_1.0"
                val root = metadata.getAsTree(format) as IIOMetadataNode
                (root.getElementsByTagName("GraphicControlExtension").item(0) as IIOMetadataNode).apply {
                    setAttribute("disposalMethod", "doNotDispose")
                    setAttribute("delayTime", frame.delayCs.toString())
                }
                (root.getElementsByTagName("ImageDescriptor").item(0) as IIOMetadataNode).apply {
                    setAttribute("imageLeftPosition", frame.left.toString())
                    setAttribute("imageTopPosition", frame.top.toString())
                }
                if (index == 0 && loopCount != null) {
                    val extensions = IIOMetadataNode("ApplicationExtensions")
                    extensions.appendChild(
                        IIOMetadataNode("ApplicationExtension").apply {
                            setAttribute("applicationID", "NETSCAPE")
                            setAttribute("authenticationCode", "2.0")
                            userObject = byteArrayOf(0x1, (loopCount and 0xFF).toByte(), ((loopCount shr 8) and 0xFF).toByte())
                        }
                    )
                    root.appendChild(extensions)
                }
                metadata.setFromTree(format, root)
                writer.writeToSequence(IIOImage(frame.image, null, metadata), param)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
        return out.toByteArray()
    }
}
