package com.jfaf.irc.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.jfaf.irc.domain.chat.MircColorParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadableTextTest {
    private val darkBackground = NeutralBackground
    private val lightBackground = LightBackground

    /** Los 16 colores de mIRC tal como los entrega el parser. */
    private fun mircColors(): List<Color> = (0..15).map { code ->
        val text = "" + code.toString().padStart(2, '0') + "x"
        MircColorParser.parse(text).spanStyles.single().item.color
    }

    @Test
    fun todosLosColoresDeMircSeLeenSobreElFondoOscuro() {
        mircColors().forEachIndexed { code, color ->
            val fixed = ReadableColors.ensureContrast(color, darkBackground)
            assertTrue(ReadableColors.contrast(fixed, darkBackground) >= ReadableColors.MIN_CONTRAST, "mIRC $code: $fixed")
        }
    }

    @Test
    fun todosLosColoresDeMircSeLeenSobreElFondoClaro() {
        mircColors().forEachIndexed { code, color ->
            val fixed = ReadableColors.ensureContrast(color, lightBackground)
            assertTrue(ReadableColors.contrast(fixed, lightBackground) >= ReadableColors.MIN_CONTRAST, "mIRC $code: $fixed")
        }
    }

    @Test
    fun elNegroSeAclaraSobreOscuroYElBlancoSeOscureceSobreClaro() {
        val black = Color.Black
        val white = Color.White
        assertTrue(ReadableColors.ensureContrast(black, darkBackground) != black)
        assertTrue(ReadableColors.ensureContrast(white, lightBackground) != white)
    }

    @Test
    fun unColorQueYaContrastaNoSeToca() {
        assertEquals(Color.White, ReadableColors.ensureContrast(Color.White, darkBackground))
        assertEquals(Color.Black, ReadableColors.ensureContrast(Color.Black, lightBackground))
        assertEquals(Color.Unspecified, ReadableColors.ensureContrast(Color.Unspecified, darkBackground))
    }

    @Test
    fun sinColoresPropiosElTextoQuedaIgual() {
        val plain = AnnotatedString("hola")
        assertEquals(plain, plain.readableOn(darkBackground))
    }

    @Test
    fun conservaTextoYTramosYSoloCambiaElColor() {
        val text = buildAnnotatedString {
            append("a ")
            withStyle(SpanStyle(color = Color.Black)) { append("negro") }
            append(" b")
        }
        val readable = text.readableOn(darkBackground)
        assertEquals(text.text, readable.text)
        val span = readable.spanStyles.single()
        assertEquals(2, span.start)
        assertEquals(7, span.end)
        assertTrue(ReadableColors.contrast(span.item.color, darkBackground) >= ReadableColors.MIN_CONTRAST)
    }

    @Test
    fun conFondoPropioDeMircMideContraConEseFondo() {
        // Blanco sobre amarillo (mIRC 0,8): sobre el fondo oscuro del chat se vería, pero sobre su amarillo no
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White, background = Color.Yellow)) { append("x") }
        }
        val fixed = text.readableOn(darkBackground).spanStyles.single().item
        assertEquals(Color.Yellow, fixed.background)
        assertTrue(ReadableColors.contrast(fixed.color, Color.Yellow) >= ReadableColors.MIN_CONTRAST)
    }
}
