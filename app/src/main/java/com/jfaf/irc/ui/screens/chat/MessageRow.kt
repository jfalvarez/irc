package com.jfaf.irc.ui.screens.chat

import android.util.Log
import android.util.Patterns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jfaf.irc.ui.chat.MediaPreview 
import com.jfaf.irc.ui.viewmodels.MediaTypeEnum // Import MediaTypeEnum
import com.jfaf.irc.ui.viewmodels.UiChatMessage
import com.jfaf.irc.ui.viewmodels.UiMessageType

@Composable
fun MessageRow(message: UiChatMessage, onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit) {
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

        val matcher = Patterns.WEB_URL.matcher(baseString.text)
        while (matcher.find()) {
            val url = matcher.group()
            if (url != null) {
                val startIndex = matcher.start()
                val endIndex = matcher.end()
                if (url != message.mediaUrl) { 
                    addStyle(
                        style = SpanStyle(
                            color = Color.White, 
                            textDecoration = TextDecoration.Underline
                        ),
                        start = startIndex,
                        end = endIndex
                    )
                    addStringAnnotation(
                        tag = "URL",
                        annotation = url,
                        start = startIndex,
                        end = endIndex
                    )
                }
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()) {
        ClickableText(
            text = clickableText,
            style = TextStyle(fontSize = 14.sp), 
            onClick = { offset ->
                clickableText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        try {
                            uriHandler.openUri(annotation.item)
                        } catch (e: Exception) {
                            Log.e("MessageRow", "Could not open URL ${annotation.item}", e)
                        }
                    }
            }
        )
        
        if (!message.mediaUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            MediaPreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp) 
                    .clickable { 
                        message.mediaUrl?.let { mediaUrl -> // Ensure mediaUrl is not null
                            onMediaClick(mediaUrl, message.mediaType) // Pass mediaType as well
                        } 
                    },
                mediaUrl = message.mediaUrl,
                mediaType = message.mediaType
            )
        }
    }
}
