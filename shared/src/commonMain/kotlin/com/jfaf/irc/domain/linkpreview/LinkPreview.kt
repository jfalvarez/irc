package com.jfaf.irc.domain.linkpreview

/** La vista previa de un enlace (UI-31): lo que la web dice de sí misma (Open Graph). Al menos hay título o descripción. */
data class LinkPreview(
    /** El enlace tal como se pidió: a él lleva la tarjeta al pulsarla. */
    val url: String,
    val siteName: String,
    val title: String?,
    val description: String?,
    /** Miniatura (`og:image`) ya como URL http(s) absoluta; null si no hay. */
    val imageUrl: String?
)

/** Una página descargada: la URL final (tras redirecciones) y su HTML, recortado al tamaño máximo. */
class FetchedPage(val finalUrl: String, val html: String)

/**
 * Descarga el HTML de una página para sacarle la vista previa. La implementación es de cada plataforma (en Android y
 * escritorio, OkHttp) y debe cumplir las reglas de seguridad de [UrlSafety]: sólo webs públicas por http(s), sin
 * cookies, con tiempo y tamaño máximos. Devuelve null ante cualquier fallo o si no es HTML.
 */
interface LinkPreviewFetcher {
    suspend fun fetch(url: String): FetchedPage?
}
