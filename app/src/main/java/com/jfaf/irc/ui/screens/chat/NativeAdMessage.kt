package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun NativeAdMessage(nativeAd: NativeAd) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                nativeAd.icon?.drawable?.let {
                    Image(
                        painter = rememberAsyncImagePainter(model = it),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Column {
                    nativeAd.headline?.let { 
                        Text(text = it, style = MaterialTheme.typography.titleMedium)
                    }
                    nativeAd.body?.let { 
                        Text(text = it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            nativeAd.mediaContent?.let { mediaContent ->
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { context ->
                        com.google.android.gms.ads.nativead.MediaView(context).apply {
                            this.mediaContent = mediaContent
                        }
                    }
                )
            }
        }
    }
}