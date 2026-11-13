package com.jfaf.irc.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.video_open_in_browser
import org.jetbrains.compose.resources.stringResource

/** Primera versión en escritorio: el vídeo se abre en el navegador (más adelante, vlcj o JavaFX). */
@Composable
actual fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier,
    isPreview: Boolean
) {
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "▶ " + stringResource(Res.string.video_open_in_browser),
            color = Color.White,
            modifier = Modifier
                .clickable { uriHandler.openUri(videoUrl) }
                .padding(16.dp)
        )
    }
}
