package com.jfaf.irc.ui.media

/**
 * Qué vídeos sabe reproducir dentro de la app el motor nativo de cada sistema (Media Foundation en Windows,
 * GStreamer en Linux, AVPlayer en macOS). Es la tabla de compatibilidad de ComposeMediaPlayer: MP4 con H.264 lo
 * reproducen los tres, pero WebM y MKV sólo GStreamer (Linux). Lo demás se abre en el navegador.
 */
internal object DesktopVideoSupport {

    private val everywhere = setOf("mp4", "m4v", "mov")
    private val linuxOnly = setOf("webm", "mkv", "avi", "flv")

    fun canPlayInApp(url: String, osName: String = System.getProperty("os.name").orEmpty()): Boolean {
        val extension = extensionOf(url)
        return when (extension) {
            in everywhere -> true
            in linuxOnly -> osName.contains("linux", ignoreCase = true)
            else -> false
        }
    }

    /** La extensión del archivo de la URL en minúsculas, sin consulta ni fragmento (`""` si no tiene). */
    fun extensionOf(url: String): String {
        val withoutQuery = url.substringBefore('#').substringBefore('?')
        // Sin esquema ni host: el punto de `cdn.x.org` no es una extensión
        val route = withoutQuery.substringAfter("://").substringAfter('/', "")
        val lastSegment = route.substringAfterLast('/')
        return if ('.' in lastSegment) lastSegment.substringAfterLast('.').lowercase() else ""
    }
}
