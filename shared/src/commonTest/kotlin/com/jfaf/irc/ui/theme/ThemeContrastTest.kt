package com.jfaf.irc.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/** UI-3: los pares de colores de texto del tema llegan a 4,5:1 (WCAG AA, texto normal). */
class ThemeContrastTest {

    private fun channel(c: Float): Double =
        if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(fg: Color, bg: Color): Double {
        val l1 = luminance(fg.compositeOver(bg))
        val l2 = luminance(bg)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun assertAa(name: String, fg: Color, bg: Color, min: Double = 4.5) {
        val ratio = contrast(fg, bg)
        assertTrue(ratio >= min, "$name: contraste $ratio < $min")
    }

    @Test
    fun textoPrincipalYSecundarioSobreElFondo() {
        assertAa("principal/fondo", NeutralOnSurface, NeutralBackground)
        assertAa("avisos/fondo", NeutralOnSurfaceMuted, NeutralBackground)
        assertAa("enlaces/fondo", LinkBlue, NeutralBackground)
    }

    @Test
    fun textoSobreSuperficiesYContenedores() {
        assertAa("principal/superficie", NeutralOnSurface, NeutralSurface)
        assertAa("avisos/superficie", NeutralOnSurfaceMuted, NeutralSurface)
        assertAa("principal/contenedor", NeutralOnSurface, NeutralSurfaceVariant)
        assertAa("avisos/contenedor", NeutralOnSurfaceMuted, NeutralSurfaceVariant)
    }

    @Test
    fun textoBlancoSobreLaMarca() {
        assertAa("blanco/morado", IRCWhite, PurpleStart)
        assertAa("blanco/azul", IRCWhite, BlueEnd)
    }

    @Test
    fun bordesDeComponentesAlMenos3a1() {
        assertAa("borde/fondo", NeutralOutline, NeutralBackground, min = 3.0)
    }

    @Test
    fun modoClaro() {
        assertAa("principal/fondo", LightOnSurface, LightBackground)
        assertAa("avisos/fondo", LightOnSurfaceMuted, LightBackground)
        assertAa("principal/superficie", LightOnSurface, LightSurface)
        assertAa("avisos/superficie", LightOnSurfaceMuted, LightSurface)
        assertAa("principal/contenedor", LightOnSurface, LightSurfaceVariant)
        assertAa("avisos/contenedor", LightOnSurfaceMuted, LightSurfaceVariant)
        assertAa("enlaces/fondo", LinkBlueLight, LightBackground)
        assertAa("enlaces/superficie", LinkBlueLight, LightSurface)
        assertAa("borde/fondo", LightOutline, LightBackground, min = 3.0)
    }
}
