package com.jfaf.irc.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.jfaf.irc.domain.chat.NickColors

/**
 * Colores de los nicks (UI-4). Cada modo tiene su paleta: en el oscuro, tonos claros que se leen sobre
 * el fondo y las superficies; en el claro, tonos oscuros. El índice sale de `NickColors.paletteIndex`.
 * `ThemeContrastTest` comprueba que todos llegan a 4,5:1 sobre el fondo y la superficie de su modo.
 */
object NickPalette {
    val dark: List<Color> = listOf(
        Color(0xFFFF8A80), Color(0xFFFFB74D), Color(0xFFFFD54F), Color(0xFFAED581),
        Color(0xFF4DD0A1), Color(0xFF4DD0E1), Color(0xFF64B5F6), Color(0xFF9FA8DA),
        Color(0xFFBA9CF5), Color(0xFFF48FB1), Color(0xFFE57FD8), Color(0xFF80CBC4)
    )

    val light: List<Color> = listOf(
        Color(0xFFC62828), Color(0xFFBF360C), Color(0xFF7A5F00), Color(0xFF2B7230),
        Color(0xFF00796B), Color(0xFF00697C), Color(0xFF1565C0), Color(0xFF3949AB),
        Color(0xFF6A1B9A), Color(0xFFAD1457), Color(0xFF9C27B0), Color(0xFF5D4037)
    )

    init {
        check(dark.size == NickColors.PALETTE_SIZE && light.size == NickColors.PALETTE_SIZE)
    }

    fun color(nick: String, darkTheme: Boolean): Color {
        val palette = if (darkTheme) dark else light
        return palette[NickColors.paletteIndex(nick)]
    }

    /** Color de la inicial sobre el círculo del avatar: oscuro sobre los tonos claros y blanco sobre los oscuros. */
    fun onAvatar(darkTheme: Boolean): Color = if (darkTheme) NeutralBackground else IRCWhite
}

/** Color del nick en el modo activo; el mismo para su avatar, su nombre y la lista de usuarios. */
@Composable
@ReadOnlyComposable
fun nickColor(nick: String): Color = NickPalette.color(nick, LocalDarkTheme.current)

/** Tu propio nick (UI-8): el morado de la marca, en un tono que se lea sobre el modo activo. */
val IRCTheme.ownNickColor: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) OwnNickDark else PurpleStart
