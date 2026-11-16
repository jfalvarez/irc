package com.jfaf.irc.domain.linkpreview

/**
 * Saca la vista previa del HTML de una página (UI-31): Open Graph (`og:`), con Twitter Cards y las etiquetas
 * corrientes (`<title>`, `description`) como respaldo. No ejecuta ni interpreta nada: sólo lee etiquetas `<meta>`.
 */
object LinkPreviewParser {

    const val MAX_TITLE = 150
    const val MAX_DESCRIPTION = 300
    const val MAX_SITE_NAME = 60

    /** Sólo se mira lo que haya antes de `</head>` (o los primeros caracteres): las etiquetas están en la cabecera. */
    private const val MAX_HEAD_CHARS = 200_000

    private val metaTag = Regex("<meta\\s([^>]*)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val attribute = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    private val titleTag = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** La vista previa de [html], descargado de [pageUrl]; null si la página no dice ni título ni descripción. */
    fun parse(html: String, pageUrl: String, requestedUrl: String = pageUrl): LinkPreview? {
        val head = html.take(MAX_HEAD_CHARS).let { text ->
            val end = text.indexOf("</head>", ignoreCase = true)
            if (end >= 0) text.substring(0, end) else text
        }

        val meta = HashMap<String, String>()
        for (tag in metaTag.findAll(head)) {
            val attrs = attribute.findAll(tag.groupValues[1]).associate { m ->
                m.groupValues[1].lowercase() to (m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } })
            }
            val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: continue
            val content = attrs["content"] ?: continue
            meta.getOrPut(key) { content } // la primera manda
        }

        fun first(vararg keys: String): String? = keys.firstNotNullOfOrNull { meta[it]?.let(::clean)?.takeIf(String::isNotEmpty) }

        val title = (first("og:title", "twitter:title")
            ?: titleTag.find(head)?.groupValues?.get(1)?.let(::clean)?.takeIf(String::isNotEmpty))
            ?.truncate(MAX_TITLE)
        val description = first("og:description", "twitter:description", "description")?.truncate(MAX_DESCRIPTION)
        if (title == null && description == null) return null

        val image = first("og:image", "og:image:secure_url", "og:image:url", "twitter:image", "twitter:image:src")
            ?.let { resolveUrl(it, pageUrl) }
            ?.takeIf { UrlSafety.isPublicHttpUrl(it) }
        val site = first("og:site_name")?.truncate(MAX_SITE_NAME)
            ?: UrlSafety.hostOf(pageUrl)?.removePrefix("www.").orEmpty()

        return LinkPreview(url = requestedUrl, siteName = site, title = title, description = description, imageUrl = image)
    }

    /** Resuelve [reference] (absoluta, `//host/x`, `/x` o relativa) contra la página [base]; null si no es http(s). */
    fun resolveUrl(reference: String, base: String): String? {
        val ref = reference.trim()
        val scheme = base.substringBefore("://", "")
        val origin = scheme + "://" + base.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        val resolved = when {
            ref.startsWith("http://", ignoreCase = true) || ref.startsWith("https://", ignoreCase = true) -> ref
            ref.contains("://") || ref.startsWith("data:", ignoreCase = true) || ref.startsWith("javascript:", ignoreCase = true) -> return null
            ref.startsWith("//") -> "$scheme:$ref"
            ref.startsWith("/") -> origin + ref
            ref.isEmpty() -> return null
            else -> {
                val path = base.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
                val dir = path.substringBeforeLast('/', "")
                origin + "/" + (if (dir.isEmpty()) "" else "$dir/") + ref
            }
        }
        return resolved.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    }

    /** Sin entidades HTML ni caracteres de control, con los espacios colapsados. */
    internal fun clean(raw: String): String =
        decodeEntities(raw).filter { it >= ' ' || it == '\n' || it == '\t' }.replace(Regex("\\s+"), " ").trim()

    private fun String.truncate(max: Int): String = if (length <= max) this else take(max - 1).trimEnd() + "…"

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "hellip" to "…",
        "ndash" to "–", "mdash" to "—", "laquo" to "«", "raquo" to "»", "copy" to "©", "reg" to "®", "euro" to "€",
        "iexcl" to "¡", "iquest" to "¿", "aacute" to "á", "eacute" to "é", "iacute" to "í", "oacute" to "ó",
        "uacute" to "ú", "ntilde" to "ñ", "uuml" to "ü", "Aacute" to "Á", "Eacute" to "É", "Iacute" to "Í",
        "Oacute" to "Ó", "Uacute" to "Ú", "Ntilde" to "Ñ", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”"
    )

    private val entity = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});")

    private fun decodeEntities(text: String): String = entity.replace(text) { m ->
        val body = m.groupValues[1]
        if (body.startsWith("#")) {
            val code = if (body[1] == 'x' || body[1] == 'X') body.substring(2).toIntOrNull(16) else body.substring(1).toIntOrNull()
            code?.let(::codePointToString) ?: m.value
        } else {
            named[body] ?: m.value
        }
    }

    // Un número que no es un carácter válido (cero, sustituto, fuera de rango) se descarta
    private fun codePointToString(code: Int): String = when {
        code <= 0 || code > 0x10FFFF || code in 0xD800..0xDFFF -> ""
        code < 0x10000 -> code.toChar().toString()
        else -> {
            val c = code - 0x10000
            charArrayOf((0xD800 + (c shr 10)).toChar(), (0xDC00 + (c and 0x3FF)).toChar()).concatToString()
        }
    }
}
