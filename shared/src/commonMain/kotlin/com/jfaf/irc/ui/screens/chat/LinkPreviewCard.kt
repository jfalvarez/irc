package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jfaf.irc.domain.linkpreview.LinkPreview
import com.jfaf.irc.domain.linkpreview.LinkPreviewRepository
import com.jfaf.irc.log.Log
import com.jfaf.irc.ui.media.MediaImage
import com.jfaf.irc.ui.theme.IRCTheme
import org.koin.compose.koinInject

/**
 * La vista previa del enlace de un mensaje (UI-31): la pide al [LinkPreviewRepository] (que la recuerda y limita las
 * descargas) y, si hay, la pinta debajo del mensaje. Mientras llega, o si la web no dice nada, no se pinta nada.
 * Sólo se llama con el ajuste activado.
 */
@Composable
fun LinkPreviewHost(url: String, modifier: Modifier = Modifier) {
    val repository: LinkPreviewRepository = koinInject()
    val preview by produceState<LinkPreview?>(initialValue = null, key1 = url) { value = repository.preview(url) }
    preview?.let { LinkPreviewCard(it, modifier) }
}

/** La tarjeta: franja de la marca, sitio, título, descripción y miniatura; al pulsarla se abre el enlace. */
@Composable
fun LinkPreviewCard(preview: LinkPreview, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = modifier
            .padding(top = 6.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.large) // esquinas de 12 dp, como las imágenes (UI-17)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button) {
                try {
                    uriHandler.openUri(preview.url)
                } catch (e: Exception) {
                    Log.e("LinkPreviewCard", "Could not open URL ${preview.url}", e)
                }
            }
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Column(modifier = Modifier.weight(1f).padding(10.dp)) {
            if (preview.siteName.isNotEmpty()) {
                Text(
                    preview.siteName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            preview.title?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    color = IRCTheme.linkColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            preview.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            preview.imageUrl?.let { image ->
                Spacer(Modifier.height(8.dp))
                MediaImage(
                    url = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    memoryCacheKey = image,
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium)
                )
            }
        }
    }
}
