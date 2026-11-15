package com.jfaf.irc.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import com.jfaf.irc.data.prefs.MessageDensity
import com.jfaf.irc.domain.chat.stableKeys
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jfaf.irc.domain.chat.ChatTimeline
import com.jfaf.irc.domain.chat.DayLabel
import com.jfaf.irc.domain.chat.TimelineItem
import com.jfaf.irc.domain.chat.model.MediaTypeEnum
import com.jfaf.irc.domain.chat.model.UiChatMessage
import com.jfaf.irc.domain.chat.model.UiMessageType
import com.jfaf.irc.domain.platform.AdHandle
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.cd_jump_to_end
import com.jfaf.irc.ui.platform.LocalPlatformUi
import com.jfaf.irc.ui.viewmodels.MainViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** A partir de cuántos elementos por encima del final aparece el botón «ir al último mensaje». */
private const val JUMP_BUTTON_THRESHOLD = 3

/** Duración (ms) del fundido de un mensaje que llega y del desplazamiento de los demás (UI-17). */
private const val ARRIVAL_ANIMATION_MS = 200

/** Espacio que queda entre el borde de arriba y el separador «Nuevos mensajes», en píxeles. */
private const val SEPARATOR_TOP_MARGIN_PX = 160

/** Lo que se espera a que llegue la lista de la pestaña nueva antes de dar por hecho que no hay mensajes nuevos. */
private const val SEPARATOR_WAIT_MS = 250L

@Composable
fun MessagesList(
    messages: List<UiChatMessage>,
    onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit,
    modifier: Modifier = Modifier,
    showMediaPreviews: Boolean,
    ad: AdHandle?,
    mainViewModel: MainViewModel,
    /** Mensajes nuevos desde el final (separador «Nuevos mensajes»); 0 = ninguno. */
    newMessagesFromEnd: Int = 0,
    /** Tu nick, para resaltar las menciones. */
    ownNick: String? = null,
    /** Pestaña que se muestra: al cambiar, la lista vuelve a donde te quedaste (ver más abajo). */
    activeTarget: String? = null,
    /** Densidad de los mensajes: cómoda o compacta (UI-16). */
    density: MessageDensity = MessageDensity.COMFORTABLE
) {
    val platformUi = LocalPlatformUi.current
    // La lista se pinta del último al primero: la línea de tiempo se construye en orden cronológico y se invierte
    val chronological = remember(messages, newMessagesFromEnd, ownNick) {
        ChatTimeline.build(messages, newMessagesFromEnd, ownNick, dayOf = { DayLabel.epochDay(it) })
    }
    val timeline = remember(chronological) { chronological.reversed() }
    // Con claves estables Compose anima lo que llega (aparece con un fundido) y desliza el resto en vez de repintarlo
    val keys = remember(chronological) { chronological.stableKeys().reversed() }
    val today = remember(messages) { DayLabel.epochDay(Clock.System.now().toEpochMilliseconds()) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Con el final abajo (reverseLayout), el elemento 0 es el último mensaje
    val showJumpButton by remember { derivedStateOf { listState.firstVisibleItemIndex > JUMP_BUTTON_THRESHOLD } }

    // Al volver a una pestaña con mensajes sin leer, la lista se coloca en el primero sin leer (justo debajo del
    // separador «Nuevos mensajes»); si no hay nuevos, al final. Sin esto conservaría el desplazamiento de la pestaña anterior.
    val currentTimeline by rememberUpdatedState(timeline)
    LaunchedEffect(activeTarget) {
        // La lista de la pestaña nueva llega por un flujo, un instante después del cambio: se espera al separador
        // (un momento como mucho; si no llega, es que no hay mensajes nuevos)
        withTimeoutOrNull(SEPARATOR_WAIT_MS) {
            snapshotFlow { currentTimeline.any { it is TimelineItem.NewMessages } }.first { it }
        }
        withFrameNanos { }
        val separator = currentTimeline.indexOfFirst { it is TimelineItem.NewMessages }
        if (separator < 0) {
            listState.scrollToItem(0)
        } else {
            // La lista va del final al principio: el elemento queda en el borde de abajo y lo nuevo, fuera de vista.
            // Se sube lo que mide la ventana (menos un margen) para dejar el separador arriba y lo nuevo debajo;
            // si no hay tanto contenido nuevo, la lista se queda abajo.
            listState.scrollToItem(separator)
            val viewport = listState.layoutInfo.viewportSize.height
            listState.scrollBy((viewport - SEPARATOR_TOP_MARGIN_PX).coerceAtLeast(0).toFloat())
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(count = timeline.size, key = { keys[it] }) { index ->
                val item = timeline[index]
                Box(modifier = Modifier.animateItem(fadeInSpec = tween(ARRIVAL_ANIMATION_MS), placementSpec = tween(ARRIVAL_ANIMATION_MS), fadeOutSpec = null)) {
                when (item) {
                    is TimelineItem.Message -> {
                        if (item.message.type == UiMessageType.AD_MESSAGE) {
                            // Anuncio: lo pinta la plataforma (en escritorio no hay)
                            ad?.let { platformUi.AdContent(it) }
                        } else {
                            MessageRow(
                                message = item.message,
                                onMediaClick = onMediaClick,
                                showMediaPreviews = showMediaPreviews,
                                mainViewModel = mainViewModel,
                                showHeader = item.showHeader,
                                isMention = item.isMention,
                                density = density
                            )
                        }
                    }
                    is TimelineItem.PresenceGroup -> PresenceGroupRow(
                        group = item,
                        expanded = expandedGroups[item.key] == true,
                        onToggle = { expandedGroups[item.key] = expandedGroups[item.key] != true },
                        onMediaClick = onMediaClick,
                        showMediaPreviews = showMediaPreviews,
                        mainViewModel = mainViewModel,
                        density = density
                    )
                    is TimelineItem.DateSeparator -> DateSeparatorRow(item.epochDay, today)
                    TimelineItem.NewMessages -> NewMessagesSeparatorRow()
                }
                }
            }
        }

        AnimatedVisibility(
            visible = showJumpButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(Res.string.cd_jump_to_end))
            }
        }
    }
}
