package com.jfaf.irc.ui.screens.chat

import com.jfaf.irc.log.Log
import com.jfaf.irc.domain.chat.LinkFinder
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.withStyle
import com.jfaf.irc.data.prefs.MessageDensity
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
// import androidx.compose.ui.res.stringResource // No longer needed for placeholder text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
// import com.jfaf.irc.R // No longer needed for string resources of placeholder
import com.jfaf.irc.ui.media.MediaPreview
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.ui.theme.mentionAccent
import com.jfaf.irc.ui.theme.mentionBackground
import com.jfaf.irc.ui.theme.nickColor
import com.jfaf.irc.ui.theme.ownNickColor
import com.jfaf.irc.domain.chat.MessageGrouping
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.domain.chat.model.MediaTypeEnum
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType

@Composable
fun MessageRow(
    message: UiChatMessage, 
    onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit,
    showMediaPreviews: Boolean,
    mainViewModel: MainViewModel,
    showHeader: Boolean = true,
    /** El mensaje nombra tu nick: fondo tenue y borde de acento (UI-5). */
    isMention: Boolean = false,
    /** Densidad (UI-16): cómoda = avatar y cabecera; compacta = una línea `<nick> texto`. */
    density: MessageDensity = MessageDensity.COMFORTABLE
) {
    var nickMenuExpanded by remember { mutableStateOf(false) }
    // Los mensajes de conversación se pintan con avatar y nick (cabecera, una vez por tanda) y el cuerpo debajo
    val speech = remember(message) { MessageGrouping.speech(message) }
    val speaker = speech?.nick
    val canOpenNickMenu = speaker != null && message.type == UiMessageType.CHANNEL_MSG_RECEIVED
    val isOwn = message.isOwnMessage
    val compact = density == MessageDensity.COMPACT
    // En la densidad compacta el nick va en la misma línea que el texto
    val inlineNick = speaker != null && compact
    val nickColorValue = when {
        speaker == null -> Color.Unspecified
        isOwn -> IRCTheme.ownNickColor
        else -> nickColor(speaker)
    }
    val baseTextColor = when (message.type) {
        UiMessageType.SYSTEM_MESSAGE, UiMessageType.JOIN_PART_QUIT, UiMessageType.NICK_CHANGE, UiMessageType.MODE_CHANGE -> MaterialTheme.colorScheme.onSurfaceVariant
        UiMessageType.SERVER_INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        UiMessageType.NOTICE -> MaterialTheme.colorScheme.onBackground
        else -> MaterialTheme.colorScheme.onBackground
    }
    val fontStyle = when (message.type) {
        UiMessageType.SYSTEM_MESSAGE, 
        UiMessageType.JOIN_PART_QUIT, 
        UiMessageType.NICK_CHANGE, 
        UiMessageType.MODE_CHANGE, 
        UiMessageType.SERVER_INFO, 
        UiMessageType.NOTICE,
        UiMessageType.ACTION_MSG -> FontStyle.Italic
        else -> FontStyle.Normal
    }
    val fontWeight = FontWeight.Normal

    val uriHandler = LocalUriHandler.current
    val clickableText = buildAnnotatedString {
        val baseString = speech?.body ?: message.annotatedString ?: AnnotatedString(message.fullText)
        var offset = 0
        if (inlineNick && speaker != null) {
            val prefix = "<$speaker> "
            withStyle(SpanStyle(color = nickColorValue, fontWeight = FontWeight.Bold)) { append(prefix) }
            offset = prefix.length
            // Sólo el nick de los mensajes de canal recibidos abre el menú contextual.
            if (canOpenNickMenu) {
                addLink(
                    clickable = LinkAnnotation.Clickable(
                        tag = "nick",
                        // Estilo explícito: con styles = null, el Text de Material3 pinta el enlace con el color primario y subrayado
                        styles = TextLinkStyles(style = SpanStyle(color = nickColorValue, fontWeight = FontWeight.Bold)),
                        linkInteractionListener = { nickMenuExpanded = true }
                    ),
                    start = 0,
                    end = prefix.length - 1
                )
            }
        }
        append(baseString)

        addStyle(SpanStyle(color = baseTextColor, fontStyle = fontStyle, fontWeight = fontWeight), offset, length)

        for (link in LinkFinder.find(baseString.text)) {
            val mediaUrl = message.mediaUrl
            val isMediaLink = mediaUrl != null &&
                mediaUrl.removePrefix("https://").removePrefix("http://") ==
                    link.text.removePrefix("https://").removePrefix("http://")
            if (!isMediaLink || !showMediaPreviews) {
                val target = link.target
                addLink(
                    url = LinkAnnotation.Url(
                        url = target,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = IRCTheme.linkColor,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = {
                            try {
                                uriHandler.openUri(target)
                            } catch (e: Exception) {
                                Log.e("MessageRow", "Could not open URL $target", e)
                            }
                        }
                    ),
                    start = link.start + offset,
                    end = link.end + offset
                )
            }
        }
    }

    val mentionAccent = IRCTheme.mentionAccent
    val mentionBackground = IRCTheme.mentionBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isMention) {
                    Modifier
                        .background(mentionBackground)
                        .drawBehind { drawRect(mentionAccent, size = Size(3.dp.toPx(), size.height)) }
                } else {
                    Modifier
                }
            )
            .padding(
                start = 8.dp, end = 8.dp,
                top = if (compact) 1.dp else if (speech != null && showHeader) 10.dp else 2.dp,
                bottom = if (compact) 1.dp else 2.dp
            )
    ) {
        if (speaker != null && !compact) {
            // Columna del avatar: sólo la primera línea de la tanda lo muestra; las demás dejan el hueco
            Box(modifier = Modifier.width(NICK_AVATAR_SIZE)) {
                if (showHeader) {
                    NickAvatar(
                        nick = speaker,
                        isOwn = isOwn,
                        modifier = if (canOpenNickMenu) Modifier.clickable { nickMenuExpanded = true } else Modifier
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (speaker != null && showHeader && !compact) {
                // Sólo el nick de los mensajes de canal recibidos abre el menú contextual.
                Box {
                    Text(
                        text = speaker,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isOwn) IRCTheme.ownNickColor else nickColor(speaker),
                        modifier = if (canOpenNickMenu) Modifier.clickable { nickMenuExpanded = true } else Modifier
                    )
                    if (canOpenNickMenu) {
                        UserActionsMenu(
                            userName = speaker,
                            expanded = nickMenuExpanded,
                            onDismiss = { nickMenuExpanded = false },
                            mainViewModel = mainViewModel
                        )
                    }
                }
            }
            Box {
                Text(
                    text = clickableText,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (inlineNick && canOpenNickMenu && speaker != null) {
                    UserActionsMenu(
                        userName = speaker,
                        expanded = nickMenuExpanded,
                        onDismiss = { nickMenuExpanded = false },
                        mainViewModel = mainViewModel
                    )
                }
            }

            val mediaUrl = message.mediaUrl
            if (!mediaUrl.isNullOrBlank() && showMediaPreviews) {
                Spacer(modifier = Modifier.height(4.dp))
                MediaPreview(
                    mediaUrl = mediaUrl,
                    mediaType = message.mediaType,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.large) // esquinas de 12 dp (UI-17)
                        .clickable { onMediaClick(mediaUrl, message.mediaType) }
                )
            }
            // No 'else' block, so no placeholder text if showMediaPreviews is false
        }
    }
}
