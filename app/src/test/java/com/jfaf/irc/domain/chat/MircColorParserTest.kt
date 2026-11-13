package com.jfaf.irc.domain.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MircColorParserTest {

    private val bold = '\u0002'
    private val color = '\u0003'
    private val underline = '\u001F'
    private val reset = '\u000F'

    @Test
    fun textoPlano_sinEstilosEspeciales() {
        val result = MircColorParser.parse("hola mundo")
        assertEquals("hola mundo", result.text)
        assertTrue(result.spanStyles.all { it.item.fontWeight == null && it.item.color == Color.Unspecified })
    }

    @Test
    fun codigosDeControlNoAparecenEnElTexto() {
        val result = MircColorParser.parse("${bold}fuerte$bold y ${color}4rojo$color fin")
        assertEquals("fuerte y rojo fin", result.text)
    }

    @Test
    fun negrita_seActivaYDesactivaConElMismoCodigo() {
        val result = MircColorParser.parse("a${bold}b${bold}c")
        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, boldSpans.size)
        assertEquals("b", result.text.substring(boldSpans[0].start, boldSpans[0].end))
    }

    @Test
    fun colorDeTexto_codigo4EsRojo() {
        val result = MircColorParser.parse("${color}4rojo")
        assertEquals(Color.Red, result.spanStyles.single().item.color)
    }

    @Test
    fun colorConFondo_seAplicanAmbos() {
        val result = MircColorParser.parse("${color}1,0texto")
        val style = result.spanStyles.single().item
        assertEquals(Color.Black, style.color)
        assertEquals(Color.White, style.background)
    }

    @Test
    fun reset_quitaFormato() {
        val result = MircColorParser.parse("${bold}${underline}a${reset}b")
        val a = result.spanStyles.first { result.text.substring(it.start, it.end) == "a" }.item
        assertEquals(FontWeight.Bold, a.fontWeight)
        assertEquals(TextDecoration.Underline, a.textDecoration)
        val b = result.spanStyles.first { result.text.substring(it.start, it.end) == "b" }.item
        assertTrue(b.fontWeight != FontWeight.Bold)
        assertTrue(b.textDecoration != TextDecoration.Underline)
    }

    @Test
    fun codigoDeColorSinNumero_restauraElColorPorDefecto() {
        val result = MircColorParser.parse("${color}4rojo${color}normal")
        val normal = result.spanStyles.first { result.text.substring(it.start, it.end) == "normal" }.item
        assertEquals(Color.Unspecified, normal.color)
    }

    @Test
    fun textoVacio_noFalla() {
        assertEquals("", MircColorParser.parse("").text)
    }
}
