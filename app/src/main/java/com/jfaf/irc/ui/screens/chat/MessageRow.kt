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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.jfaf.irc.ui.viewmodels.UiChatMessage
import com.jfaf.irc.ui.viewmodels.UiMessageType

@Composable
fun MessageRow(message: UiChatMessage, onImageClick: (String) -> Unit) {
    val baseTextColor = when (message.type) {
        UiMessageType.SYSTEM_MESSAGE, UiMessageType.JOIN_PART_QUIT, UiMessageType.NICK_CHANGE, UiMessageType.MODE_CHANGE -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        UiMessageType.SERVER_INFO -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
        UiMessageType.NOTICE -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val fontStyle = when (message.type) {
        UiMessageType.SYSTEM_MESSAGE, UiMessageType.JOIN_PART_QUIT, UiMessageType.NICK_CHANGE, UiMessageType.MODE_CHANGE, UiMessageType.SERVER_INFO, UiMessageType.NOTICE -> FontStyle.Italic
        else -> FontStyle.Normal
    }
    val fontWeight = if (message.isOwnMessage && message.annotatedString?.spanStyles?.all { it.item.fontWeight != FontWeight.Bold } == true) FontWeight.Bold else FontWeight.Normal

    val uriHandler = LocalUriHandler.current
    val clickableText = buildAnnotatedString {
        val baseString = message.annotatedString ?: AnnotatedString(message.fullText)
        append(baseString)

        // Override the base color, fontStyle, and fontWeight for the entire text initially
        // These will be overridden by spans within baseString or by link styles below.
        addStyle(SpanStyle(color = baseTextColor, fontStyle = fontStyle, fontWeight = fontWeight), 0, length)

        val matcher = Patterns.WEB_URL.matcher(baseString.text)
        while (matcher.find()) {
            val url = matcher.group()
            if (url != null) {
                val startIndex = matcher.start()
                val endIndex = matcher.end()
                addStyle(
                    style = SpanStyle(
                        color = Color.White, // Link color
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

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()) {
        ClickableText(
            text = clickableText,
            style = TextStyle(fontSize = 14.sp), // Default style, color/fontStyle/fontWeight from clickableText spans
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
        
        if (!message.imageUrl.isNullOrBlank()) {
            var showImageAndSpace by remember(message.imageUrl) { mutableStateOf(true) }

            if (showImageAndSpace) {
                Spacer(modifier = Modifier.height(4.dp))
                AsyncImage(
                    model = message.imageUrl,
                    contentDescription = "Imagen adjunta: ${message.imageUrl}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp) 
                        .clickable { 
                            message.imageUrl?.let { onImageClick(it) } 
                        },
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Error) {
                            Log.w("MessageRow", "Error al cargar imagen: ${message.imageUrl}, ${state.result.throwable}")
                            showImageAndSpace = false 
                        }
                    },
                    contentScale = ContentScale.Fit 
                )
            }
        }
    }
}
