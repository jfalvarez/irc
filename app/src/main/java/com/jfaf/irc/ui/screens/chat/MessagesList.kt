package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jfaf.irc.ads.AdMobAd
import com.jfaf.irc.domain.platform.AdHandle
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.ui.model.MediaTypeEnum
import com.jfaf.irc.ui.model.UiChatMessage
import com.jfaf.irc.ui.model.UiMessageType

@Composable
fun MessagesList(
    messages: List<UiChatMessage>,
    onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit,
    modifier: Modifier = Modifier,
    showMediaPreviews: Boolean,
    ad: AdHandle?,
    mainViewModel: MainViewModel
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        items(messages.reversed()) { msg ->
            if (msg.type == UiMessageType.AD_MESSAGE) {
                // En 3.2 esto pasa a un slot de plataforma; en escritorio no hay anuncios.
                (ad as? AdMobAd)?.let { NativeAdMessage(nativeAd = it.nativeAd) }
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
