package com.jfaf.irc.ui.screens.chat

import com.jfaf.irc.log.Log
import com.jfaf.irc.domain.chat.LinkFinder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
// import androidx.compose.ui.res.stringResource // No longer needed for placeholder text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// import com.jfaf.irc.R // No longer needed for string resources of placeholder
import com.jfaf.irc.ui.chat.MediaPreview 
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.domain.chat.model.MediaTypeEnum
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType

@Composable
fun MessageRow(
    message: UiChatMessage, 
    onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit,
    showMediaPreviews: Boolean,
    mainViewModel: MainViewModel
) {
    var nickMenuExpanded by remember { mutableStateOf(false) }
    val baseTextColor = when (message.type) {
        UiMessageType.SYSTEM_MESSAGE, UiMessageType.JOIN_PART_QUIT, UiMessageType.NICK_CHANGE, UiMessageType.MODE_CHANGE -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        UiMessageType.SERVER_INFO -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
        UiMessageType.NOTICE -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimary
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
    val fontWeight = if (message.isOwnMessage && message.annotatedString?.spanStyles?.all { it.item.fontWeight != FontWeight.Bold } == true) FontWeight.Bold else FontWeight.Normal

    val uriHandler = LocalUriHandler.current
    val clickableText = buildAnnotatedString {
        val baseString = message.annotatedString ?: AnnotatedString(message.fullText)
        append(baseString)

        addStyle(SpanStyle(color = baseTextColor, fontStyle = fontStyle, fontWeight = fontWeight), 0, length)

        // Solo el "<nick>" de mensajes de canal recibidos abre el menú contextual.
        val nickSender = message.sender
        if (message.type == UiMessageType.CHANNEL_MSG_RECEIVED && !nickSender.isNullOrBlank()) {
            val nickPrefix = "<$nickSender>"
            if (baseString.text.startsWith(nickPrefix)) {
                addLink(
                    clickable = LinkAnnotation.Clickable(
                        tag = "nick",
                        styles = null,
                        linkInteractionListener = { nickMenuExpanded = true }
                    ),
                    start = 0,
                    end = nickPrefix.length
                )
            }
        }

        for (link in LinkFinder.find(baseString.text)) {
            val isMediaLink = message.mediaUrl != null &&
                message.mediaUrl.removePrefix("https://").removePrefix("http://") ==
                    link.text.removePrefix("https://").removePrefix("http://")
            if (!isMediaLink || !showMediaPreviews) {
                val target = link.target
                addLink(
                    url = LinkAnnotation.Url(
                        url = target,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = Color.White, // Consider MaterialTheme.colorScheme.primary for theming
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
                    start = link.start,
                    end = link.end
                )
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()) {
        Box {
            Text(
                text = clickableText,
                style = TextStyle(fontSize = 14.sp)
            )
            message.sender?.let { nick ->
                UserActionsMenu(
                    userName = nick,
                    expanded = nickMenuExpanded,
                    onDismiss = { nickMenuExpanded = false },
                    mainViewModel = mainViewModel
                )
            }
        }

        if (!message.mediaUrl.isNullOrBlank() && showMediaPreviews) {
            Spacer(modifier = Modifier.height(4.dp))
            MediaPreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp) 
                    .clickable {
                        onMediaClick(message.mediaUrl, message.mediaType)
                    },
                mediaUrl = message.mediaUrl,
                mediaType = message.mediaType
            )
        } 
        // No 'else' block, so no placeholder text if showMediaPreviews is false
    }
}
