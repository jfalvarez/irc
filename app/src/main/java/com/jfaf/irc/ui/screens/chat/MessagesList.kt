package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.nativead.NativeAd
import com.jfaf.irc.ads.NativeAdManager
import com.jfaf.irc.ui.viewmodels.MediaTypeEnum
import com.jfaf.irc.ui.viewmodels.UiChatMessage
import com.jfaf.irc.ui.viewmodels.UiMessageType

@Composable
fun MessagesList(
    messages: List<UiChatMessage>,
    onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit,
    modifier: Modifier = Modifier,
    showMediaPreviews: Boolean,
    nativeAdManager: NativeAdManager
) {
    val nativeAd by nativeAdManager.nativeAd.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        items(messages.reversed()) { msg ->
            if (msg.type == UiMessageType.AD_MESSAGE) {
                nativeAd?.let { ad ->
                    NativeAdMessage(nativeAd = ad)
                }
            } else {
                MessageRow(
                    message = msg,
                    onMediaClick = onMediaClick,
                    showMediaPreviews = showMediaPreviews
                )
            }
        }
    }
}
