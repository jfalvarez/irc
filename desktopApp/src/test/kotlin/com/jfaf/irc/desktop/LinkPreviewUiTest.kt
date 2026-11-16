package com.jfaf.irc.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.jfaf.irc.domain.linkpreview.LinkPreview
import com.jfaf.irc.ui.screens.chat.LinkPreviewCard
import com.jfaf.irc.ui.theme.IRCAppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** UI-31: la tarjeta de vista previa de un enlace (sin red: se le da la vista previa ya hecha). */
@OptIn(ExperimentalTestApi::class)
class LinkPreviewUiTest {

    private val preview = LinkPreview(
        url = "https://example.com/noticia",
        siteName = "Mi Diario",
        title = "Titular de prueba",
        description = "Resumen de la noticia en pocas palabras",
        imageUrl = null
    )

    @Test
    fun pintaSitioTituloYDescripcion() = runSkikoComposeUiTest(size = Size(600f, 400f)) {
        setContent { IRCAppTheme { LinkPreviewCard(preview) } }
        onNodeWithText("Mi Diario").assertExists()
        onNodeWithText("Titular de prueba").assertExists()
        onNodeWithText("Resumen de la noticia en pocas palabras").assertExists()
    }

    @Test
    fun alPulsarlaAbreElEnlace() = runSkikoComposeUiTest(size = Size(600f, 400f)) {
        val opened = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) { opened += uri }
        }
        setContent { CompositionLocalProvider(LocalUriHandler provides handler) { IRCAppTheme { LinkPreviewCard(preview) } } }

        onNodeWithText("Titular de prueba").assertExists()
        onNode(hasText("Titular de prueba") and hasClickAction()).performClick()

        assertEquals(listOf("https://example.com/noticia"), opened)
    }

    @Test
    fun sinTituloNiSitio_pintaSoloLaDescripcion() = runSkikoComposeUiTest(size = Size(600f, 400f)) {
        setContent { IRCAppTheme { LinkPreviewCard(preview.copy(siteName = "", title = null)) } }
        onNodeWithText("Resumen de la noticia en pocas palabras").assertExists()
        onNodeWithText("Mi Diario").assertDoesNotExist()
    }
}
