package com.jfaf.irc.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import kotlin.math.pow

/**
 * Hace legibles los colores que el texto trae de fábrica, como los de mIRC (paleta fija: el negro no se ve sobre el
 * fondo oscuro ni el blanco sobre el claro). Un color que ya contrasta lo bastante no se toca.
 */
object ReadableColors {
    /** Contraste mínimo que se exige a un color de mIRC sobre su fondo (AA pide 4,5; el resto es margen para las líneas resaltadas). */
    const val MIN_CONTRAST = 5.0

    private fun channel(c: Float): Double =
        if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    /** Relación de contraste WCAG entre dos colores opacos. */
    fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [fg] acercado al blanco (fondo oscuro) o al negro (fondo claro) lo justo para llegar a [min] sobre [bg]. */
    fun ensureContrast(fg: Color, bg: Color, min: Double = MIN_CONTRAST): Color {
        if (fg == Color.Unspecified || bg == Color.Unspecified) return fg
        if (contrast(fg.compositeOver(bg), bg) >= min) return fg
        val target = if (luminance(bg) < 0.5) Color.White else Color.Black
        for (step in 1..STEPS) {
            val candidate = lerp(fg, target, step / STEPS.toFloat())
            if (contrast(candidate.compositeOver(bg), bg) >= min) return candidate
        }
        return target
    }

    private const val STEPS = 20
}

/**
 * El mismo texto con los colores de primer plano legibles sobre [background]; si un tramo trae su propio color de fondo
 * (mIRC permite `fg,bg`), el contraste se mide contra ese fondo.
 */
fun AnnotatedString.readableOn(background: Color, minContrast: Double = ReadableColors.MIN_CONTRAST): AnnotatedString {
    if (spanStyles.none { it.item.color != Color.Unspecified }) return this
    return AnnotatedString(
        text = text,
        spanStyles = spanStyles.map { range ->
            val style = range.item
            if (style.color == Color.Unspecified) {
                range
            } else {
                val behind = if (style.background != Color.Unspecified) style.background.compositeOver(background) else background
                range.copy(item = style.copy(color = ReadableColors.ensureContrast(style.color, behind, minContrast)))
            }
        },
        paragraphStyles = paragraphStyles
    )
}
