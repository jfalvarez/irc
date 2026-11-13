package com.jfaf.irc.data.text

import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.allStringResources
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Todos los textos de Compose Resources (lógica y UI), tal como los verá la app. */
class AllStringsTest {

    @Test
    fun ningunTextoVacioNiConEscapesDeAndroid() = runBlocking {
        val all = Res.allStringResources
        assertTrue(all.size >= 142, "sólo hay ${all.size} textos")
        for ((name, resource) in all) {
            val text = getString(resource)
            assertTrue(text.isNotBlank(), "$name vacío")
            // Android convertía \' y \"; Compose Resources no: se verían las barras.
            assertFalse('\\' in text, "$name conserva una barra invertida: $text")
        }
    }
}
