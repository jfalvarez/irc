package com.jfaf.irc.desktop

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode

/** GIF animados de prueba generados con `ImageIO` (sin archivos binarios en el repositorio). */
object GifFixtures {
    const val RED = 0xFF0000
    const val GREEN = 0x00FF00
    const val BLUE = 0x0000FF

    /** Un GIF sin fin con un fotograma liso de 8x8 por cada color; [delayCs] en centésimas de segundo. */
    fun solidFrames(colors: List<Int>, delayCs: Int = 10): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(null)
            colors.forEachIndexed { index, rgb ->
                val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).also { img ->
                    for (x in 0 until 8) for (y in 0 until 8) img.setRGB(x, y, 0xFF000000.toInt() or rgb)
                }
                val param = writer.defaultWriteParam
                val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param)
                val format = "javax_imageio_gif_image_1.0"
                val root = metadata.getAsTree(format) as IIOMetadataNode
                (root.getElementsByTagName("GraphicControlExtension").item(0) as IIOMetadataNode).apply {
                    setAttribute("disposalMethod", "doNotDispose")
                    setAttribute("delayTime", delayCs.toString())
                }
                if (index == 0) { // extensión de bucle: 0 = sin fin
                    val extensions = IIOMetadataNode("ApplicationExtensions")
                    extensions.appendChild(
                        IIOMetadataNode("ApplicationExtension").apply {
                            setAttribute("applicationID", "NETSCAPE")
                            setAttribute("authenticationCode", "2.0")
                            userObject = byteArrayOf(0x1, 0, 0)
                        }
                    )
                    root.appendChild(extensions)
                }
                metadata.setFromTree(format, root)
                writer.writeToSequence(IIOImage(image, null, metadata), param)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
        return out.toByteArray()
    }
}
