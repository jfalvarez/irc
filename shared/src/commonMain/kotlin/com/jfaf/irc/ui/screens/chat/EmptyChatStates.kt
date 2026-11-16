package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.empty_channel_hint
import com.jfaf.irc.resources.empty_pm_hint
import com.jfaf.irc.resources.menu_item_browse_channels
import com.jfaf.irc.resources.no_channels_body
import com.jfaf.irc.resources.no_channels_title
import org.jetbrains.compose.resources.stringResource

/** Etiqueta de prueba del botón «Explorar canales» del aviso de «ningún canal». */
const val NO_CHANNELS_BROWSE_TAG = "no-channels-browse"

/**
 * Lo que se ve en una pestaña de canal o de privado sin ningún mensaje todavía (UI-37): una pista breve de qué hacer,
 * en vez de un hueco en blanco.
 */
@Composable
fun EmptyChatHint(target: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(if (target.startsWith("#")) Res.string.empty_channel_hint else Res.string.empty_pm_hint, target),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Aviso para quien acaba de conectar y no ha entrado en ningún canal (UI-37): la pestaña Servidor sólo trae el mensaje
 * del día, y no se ve por dónde seguir. Lleva al explorador de canales; el comando `/join` se menciona como alternativa.
 */
@Composable
fun NoChannelsCard(onBrowseChannels: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
            Text(
                stringResource(Res.string.no_channels_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(Res.string.no_channels_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Button(onClick = onBrowseChannels, modifier = Modifier.testTag(NO_CHANNELS_BROWSE_TAG)) {
                Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.menu_item_browse_channels), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
