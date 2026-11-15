package com.jfaf.irc.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp
)

/**
 * Tema tipográfico completo (UI-15). Los textos de la app usan estos estilos y no tamaños sueltos:
 * `bodyMedium` (14 sp) es el de los mensajes del chat; `titleMedium`, el de las cabeceras;
 * `labelSmall`, el de las insignias y los textos auxiliares.
 */
val Typography = Typography(
    headlineSmall = style(24, 32),
    titleLarge = style(20, 28, FontWeight.Medium),
    titleMedium = style(16, 24, FontWeight.Medium, 0.15),
    titleSmall = style(14, 20, FontWeight.Medium, 0.1),
    bodyLarge = style(16, 24, spacing = 0.5),
    bodyMedium = style(14, 20, spacing = 0.25),
    bodySmall = style(12, 16, spacing = 0.4),
    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.5),
    labelSmall = style(11, 16, FontWeight.Medium, 0.5)
)
