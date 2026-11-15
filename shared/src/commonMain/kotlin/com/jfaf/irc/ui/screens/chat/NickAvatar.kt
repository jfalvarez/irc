package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jfaf.irc.domain.chat.NickColors
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.ui.theme.IRCWhite
import com.jfaf.irc.ui.theme.LocalDarkTheme
import com.jfaf.irc.ui.theme.NickPalette
import com.jfaf.irc.ui.theme.nickColor

val NICK_AVATAR_SIZE = 36.dp

/**
 * Avatar con la inicial del nick (UI-9): un círculo del color de su nick (UI-4) y, en el tuyo, el degradado
 * de la marca (UI-8). IRC no tiene fotos de perfil.
 */
@Composable
fun NickAvatar(
    nick: String,
    isOwn: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = NICK_AVATAR_SIZE
) {
    val fill = if (isOwn) {
        Modifier.background(IRCTheme.gradientBrush, CircleShape)
    } else {
        Modifier.background(nickColor(nick), CircleShape)
    }
    Box(
        modifier = modifier.size(size).clip(CircleShape).then(fill),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = NickColors.initial(nick),
            color = if (isOwn) IRCWhite else NickPalette.onAvatar(LocalDarkTheme.current),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
