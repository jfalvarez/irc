package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jfaf.irc.ui.viewmodels.MediaTypeEnum 
import com.jfaf.irc.ui.viewmodels.UiChatMessage

@Composable
fun MessagesList(
    messages: List<UiChatMessage>, 
    onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit, 
    modifier: Modifier = Modifier,
    showMediaPreviews: Boolean // Nuevo parámetro
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        items(messages.reversed()) { msg ->
            MessageRow(
                message = msg, 
                onMediaClick = onMediaClick,
                showMediaPreviews = showMediaPreviews // Pasar el parámetro
            )
        }
    }
}
