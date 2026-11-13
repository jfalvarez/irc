package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jfaf.irc.ui.platform.LocalPlatformUi
import com.jfaf.irc.domain.platform.AdHandle
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.domain.chat.model.MediaTypeEnum
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType

@Composable
fun MessagesList(
    messages: List<UiChatMessage>,
    onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit,
    modifier: Modifier = Modifier,
    showMediaPreviews: Boolean,
    ad: AdHandle?,
    mainViewModel: MainViewModel
) {
    val platformUi = LocalPlatformUi.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        items(messages.reversed()) { msg ->
            if (msg.type == UiMessageType.AD_MESSAGE) {
                // Anuncio: lo pinta la plataforma (en escritorio no hay)
                ad?.let { platformUi.AdContent(it) }
            } else {
                MessageRow(
                    message = msg,
                    onMediaClick = onMediaClick,
                    showMediaPreviews = showMediaPreviews,
                    mainViewModel = mainViewModel
                )
            }
        }
    }
}
