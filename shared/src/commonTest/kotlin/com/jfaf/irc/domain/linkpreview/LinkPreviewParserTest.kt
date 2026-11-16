package com.jfaf.irc.domain.linkpreview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkPreviewParserTest {

    private val page = "https://www.example.com/noticias/uno"

    private fun parse(head: String, url: String = page) = LinkPreviewParser.parse("<html><head>$head</head><body>x</body></html>", url)

    @Test
    fun leeOpenGraph() {
        val p = assertNotNull(parse(
            """<meta property="og:title" content="Titular"><meta property="og:description" content="Resumen de la noticia">
               <meta property="og:image" content="https://cdn.example.com/a.jpg"><meta property="og:site_name" content="Mi Diario">"""
        ))
        assertEquals("Titular", p.title)
        assertEquals("Resumen de la noticia", p.description)
        assertEquals("https://cdn.example.com/a.jpg", p.imageUrl)
        assertEquals("Mi Diario", p.siteName)
        assertEquals(page, p.url)
    }

    @Test
    fun conComillasSimplesSinComillasYOtroOrdenDeAtributos() {
        val p = assertNotNull(parse("""<META content='Titular' PROPERTY='og:title'/><meta name=description content=Resumen>"""))
        assertEquals("Titular", p.title)
        assertEquals("Resumen", p.description)
    }

    @Test
    fun respaldoConTitleYDescriptionCorrientes() {
        val p = assertNotNull(parse("""<title> Mi página
            sin Open Graph </title><meta name="description" content="Una descripción">"""))
        assertEquals("Mi página sin Open Graph", p.title)
        assertEquals("Una descripción", p.description)
        assertEquals("example.com", p.siteName, "Sin og:site_name se usa el host sin www")
    }

    @Test
    fun twitterCardsComoRespaldo() {
        val p = assertNotNull(parse("""<meta name="twitter:title" content="T"><meta name="twitter:image" content="https://i.example.com/x.png">"""))
        assertEquals("T", p.title)
        assertEquals("https://i.example.com/x.png", p.imageUrl)
    }

    @Test
    fun openGraphMandaSobreElRespaldo() {
        val p = assertNotNull(parse("""<title>Del title</title><meta property="og:title" content="Del og">"""))
        assertEquals("Del og", p.title)
    }

    @Test
    fun entidadesHtml() {
        val p = assertNotNull(parse("""<meta property="og:title" content="Tom &amp; Jerry &quot;hoy&quot; &#233;l &#x1F600; a&ntilde;o &hellip;">"""))
        assertEquals("Tom & Jerry \"hoy\" él 😀 año …", p.title)
    }

    @Test
    fun entidadDesconocidaSeDejaTalCual() {
        assertEquals("a &zzzz; b", assertNotNull(parse("""<meta property="og:title" content="a &zzzz; b">""")).title)
    }

    @Test
    fun resuelveLaImagenRelativa() {
        fun image(ref: String) = parse("""<meta property="og:title" content="T"><meta property="og:image" content="$ref">""")?.imageUrl
        assertEquals("https://www.example.com/img/a.png", image("/img/a.png"))
        assertEquals("https://www.example.com/noticias/a.png", image("a.png"))
        assertEquals("https://cdn.x.com/a.png", image("//cdn.x.com/a.png"))
        assertEquals("https://otra.com/a.png", image("https://otra.com/a.png"))
    }

    @Test
    fun imagenesQueNoSonWebPublicaSeIgnoran_peroElRestoSeConserva() {
        fun preview(ref: String) = assertNotNull(parse("""<meta property="og:title" content="T"><meta property="og:image" content="$ref">"""))
        assertNull(preview("data:image/png;base64,AAAA").imageUrl)
        assertNull(preview("javascript:alert(1)").imageUrl)
        assertNull(preview("http://192.168.1.10/x.png").imageUrl)
        assertNull(preview("http://localhost/x.png").imageUrl)
        assertEquals("T", preview("http://localhost/x.png").title)
    }

    @Test
    fun sinTituloNiDescripcion_noHayVistaPrevia() {
        assertNull(parse("""<meta property="og:image" content="https://i.example.com/x.png">"""))
        assertNull(parse(""))
        assertNull(LinkPreviewParser.parse("no es html", page))
    }

    @Test
    fun acotaLosTextosLargos() {
        val p = assertNotNull(parse("""<meta property="og:title" content="${"a".repeat(500)}"><meta property="og:description" content="${"b".repeat(900)}">"""))
        assertEquals(LinkPreviewParser.MAX_TITLE, p.title!!.length)
        assertTrue(p.title!!.endsWith("…"))
        assertEquals(LinkPreviewParser.MAX_DESCRIPTION, p.description!!.length)
    }

    @Test
    fun noLeeNadaDelCuerpo() {
        // Una etiqueta meta del cuerpo (contenido del usuario de esa web) no cuenta
        val html = "<html><head><title>Real</title></head><body><meta property=\"og:title\" content=\"Falso\"></body></html>"
        assertEquals("Real", LinkPreviewParser.parse(html, page)?.title)
    }

    @Test
    fun noEjecutaNiConservaMarcado() {
        val p = assertNotNull(parse("""<meta property="og:title" content="Hola&#0;&#1; mundo"><meta property="og:description" content="  a   b
            c ">"""))
        assertEquals("Hola mundo", p.title)
        assertEquals("a b c", p.description)
    }

    @Test
    fun laUrlPedidaSeConservaAunqueHayaRedireccion() {
        val p = LinkPreviewParser.parse(
            "<head><meta property=\"og:title\" content=\"T\"><meta property=\"og:image\" content=\"/i.png\"></head>",
            pageUrl = "https://final.example.org/destino", requestedUrl = "https://corto.ly/abc"
        )!!
        assertEquals("https://corto.ly/abc", p.url)
        assertEquals("https://final.example.org/i.png", p.imageUrl)
    }
}
