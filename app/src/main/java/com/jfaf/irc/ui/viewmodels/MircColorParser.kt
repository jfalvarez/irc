package com.jfaf.irc.ui.viewmodels

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.* // Importación global para androidx.compose.ui.text
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern

object MircColorParser {

    // mIRC color code map (0-15 standard)
    private val mircColors = mapOf(
        0 to Color.White,
        1 to Color.Black,
        2 to Color(0xFF00007F), // Navy
        3 to Color(0xFF009300), // Green
        4 to Color.Red,
        5 to Color(0xFF7F0000), // Maroon
        6 to Color(0xFF9C009C), // Purple
        7 to Color(0xFFFC7F00), // Orange/Olive
        8 to Color.Yellow,
        9 to Color(0xFF00FC00), // Lime
        10 to Color(0xFF009393), // Teal/Green-Blue Cyan
        11 to Color.Cyan,       // Light Cyan
        12 to Color(0xFF0000FC), // Royal Blue
        13 to Color(0xFFFF00FF), // Pink/Magenta
        14 to Color.Gray,
        15 to Color(0xFFD2D2D2)  // Light Grey/Silver
    )

    // Control characters
    private const val BOLD = '\u0002'      // ASCII 2
    private const val COLOR = '\u0003'     // ASCII 3
    private const val ITALIC = '\u001D'    // ASCII 29 (often for italic)
    private const val UNDERLINE = '\u001F' // ASCII 31 (often for underline)
    private const val RESET = '\u000F'     // ASCII 15 (reset formatting)
    // private const val REVERSE = '\u0016' // ASCII 22 (reverse FG/BG - skip for now)

    // Regex to find control codes and text segments
    // It captures: a color code sequence, a single control char, or a block of plain text
    private val mircPattern = Pattern.compile(
        "(" + COLOR + "(\\d{1,2}(,\\d{1,2})?)?)|([" + BOLD + ITALIC + UNDERLINE + RESET + "])?([^" + BOLD + COLOR + ITALIC + UNDERLINE + RESET + "]+)?"
    )

    fun parse(text: String, defaultStyle: SpanStyle = SpanStyle()): AnnotatedString {
        return buildAnnotatedString {
            val matcher = mircPattern.matcher(text)

            var currentFgColor: Color? = defaultStyle.color
            var currentBgColor: Color? = defaultStyle.background
            var isBold = defaultStyle.fontWeight == FontWeight.Bold
            var isItalic = defaultStyle.fontStyle == FontStyle.Italic
            var isUnderline = defaultStyle.textDecoration == TextDecoration.Underline

            while (matcher.find()) {
                val colorControlSequence = matcher.group(1)
                val singleControlChar = matcher.group(4)
                val plainText = matcher.group(5)

                if (colorControlSequence != null) {
                    val parts = colorControlSequence.substring(1).split(",")
                    if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                        val fgCode = parts[0].toIntOrNull()
                        currentFgColor = mircColors[fgCode] ?: currentFgColor

                        if (parts.size > 1 && parts[1].isNotEmpty()) {
                            val bgCode = parts[1].toIntOrNull()
                            currentBgColor = mircColors[bgCode] ?: currentBgColor
                        } else {
                            // No background color specified, or only foreground.
                            // mIRC clients might reset background here. For now, we keep the existing one or default.
                            // currentBgColor = defaultStyle.background // Option to reset background
                        }
                    } else {
                        currentFgColor = defaultStyle.color
                        currentBgColor = defaultStyle.background
                    }
                } else if (singleControlChar != null) {
                    when (singleControlChar[0]) {
                        BOLD -> isBold = !isBold
                        ITALIC -> isItalic = !isItalic
                        UNDERLINE -> isUnderline = !isUnderline
                        RESET -> {
                            currentFgColor = defaultStyle.color
                            currentBgColor = defaultStyle.background
                            isBold = defaultStyle.fontWeight == FontWeight.Bold
                            isItalic = defaultStyle.fontStyle == FontStyle.Italic
                            isUnderline = defaultStyle.textDecoration == TextDecoration.Underline
                        }
                    }
                } else if (plainText != null) {
                    val startOffset = this.length
                    append(plainText)
                    val endOffset = this.length

                    val spanStyle = SpanStyle(
                        color = currentFgColor ?: Color.Unspecified,
                        background = currentBgColor ?: Color.Unspecified,
                        fontWeight = if (isBold) FontWeight.Bold else defaultStyle.fontWeight,
                        fontStyle = if (isItalic) FontStyle.Italic else defaultStyle.fontStyle,
                        textDecoration = if (isUnderline) TextDecoration.Underline else defaultStyle.textDecoration
                    )
                    addStyle(spanStyle, startOffset, endOffset)
                }
            }
        }
    }
}
