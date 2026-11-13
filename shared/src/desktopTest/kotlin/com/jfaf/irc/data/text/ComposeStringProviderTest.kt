package com.jfaf.irc.data.text

import com.jfaf.irc.domain.text.Msg
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Lee los textos reales de Compose Resources. Sólo en escritorio: en el test de host de Android
 * no hay Context (Compose Resources lo obtiene de la app en marcha).
 */
class ComposeStringProviderTest {

    private val strings = runBlocking { ComposeStringProvider.load() }

    @Test
    fun todosLosMsgTienenTexto_sinEscapesSinProcesar() {
        for (msg in Msg.entries) {
            val text = strings.get(msg, "A", "B", "C", "D")
            assertTrue(text.isNotBlank(), "$msg vacío")
            assertFalse('\\' in text, "$msg conserva una barra invertida: $text")
        }
    }

    @Test
    fun sustituyeArgumentosPosicionales() {
        assertEquals("Conectando a irc.example.org como ana...", strings.get(Msg.CONNECTING_TO, "irc.example.org", "ana"))
        assertEquals("Error 433: ocupado", strings.get(Msg.ERR_NUMERIC, "433", "ocupado"))
    }

    @Test
    fun ayudaConSaltosDeLineaReales() {
        val help = strings.get(Msg.CMD_HELP)
        assertTrue('\n' in help)
        assertTrue("/me <acción>" in help, help) // &lt; &gt; ya decodificados
    }
}
