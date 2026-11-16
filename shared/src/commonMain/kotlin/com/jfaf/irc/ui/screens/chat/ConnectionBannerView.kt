package com.jfaf.irc.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jfaf.irc.domain.chat.ConnectionBanner
import com.jfaf.irc.domain.chat.ConnectionBannerState
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.status_connection_restored
import com.jfaf.irc.resources.status_reconnecting
import com.jfaf.irc.resources.status_waiting_for_network
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Banner sobre el chat mientras se reconecta (UI-28): «Reconectando…» con una barra de progreso, o «Sin conexión a
 * Internet…» si se espera a la red, y un aviso breve de «Conexión recuperada» al volver. Los lectores de pantalla lo
 * anuncian solos (región viva). Qué muestra lo decide [ConnectionBanner].
 */
@Composable
fun ConnectionBannerView(
    isReconnecting: Boolean,
    isWaitingForNetwork: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    var restored by remember { mutableStateOf(false) }
    var wasReconnecting by remember { mutableStateOf(isReconnecting) }
    LaunchedEffect(isReconnecting) {
        val justRestored = ConnectionBanner.isRestored(wasReconnecting, isReconnecting, isConnected)
        wasReconnecting = isReconnecting
        if (justRestored) {
            restored = true
            delay(ConnectionBanner.RESTORED_VISIBLE_MILLIS)
        }
        restored = false
    }

    val state = if (restored) ConnectionBannerState.RESTORED else ConnectionBanner.of(isReconnecting, isWaitingForNetwork)
    // Durante la animación de salida el banner conserva el último contenido visible
    val lastVisible = remember { mutableStateOf(state) }
    if (state != ConnectionBannerState.HIDDEN) lastVisible.value = state

    AnimatedVisibility(
        visible = state != ConnectionBannerState.HIDDEN,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        val shown = lastVisible.value
        val ok = shown == ConnectionBannerState.RESTORED
        // Pares de color ya comprobados por ThemeContrastTest: gris de superficie para el aviso, marca al recuperar
        val container = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        val content = if (ok) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        Column(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
            Row(
                modifier = Modifier.fillMaxWidth().background(container).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (shown) {
                        ConnectionBannerState.WAITING_FOR_NETWORK -> Icons.Outlined.CloudOff
                        ConnectionBannerState.RESTORED -> Icons.Filled.CheckCircle
                        else -> Icons.Filled.Sync
                    },
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        when (shown) {
                            ConnectionBannerState.WAITING_FOR_NETWORK -> Res.string.status_waiting_for_network
                            ConnectionBannerState.RESTORED -> Res.string.status_connection_restored
                            else -> Res.string.status_reconnecting
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = content
                )
            }
            if (shown == ConnectionBannerState.RECONNECTING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
