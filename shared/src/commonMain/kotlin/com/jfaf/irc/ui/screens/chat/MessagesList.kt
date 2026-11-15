package com.jfaf.irc.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.remember
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
    ownNick: String? = null
) {
    val platformUi = LocalPlatformUi.current
    // La lista se pinta del último al primero: la línea de tiempo se construye en orden cronológico y se invierte
    val timeline = remember(messages, newMessagesFromEnd, ownNick) {
        ChatTimeline.build(messages, newMessagesFromEnd, ownNick, dayOf = { DayLabel.epochDay(it) }).reversed()
    }
    val today = remember(messages) { DayLabel.epochDay(Clock.System.now().toEpochMilliseconds()) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Con el final abajo (reverseLayout), el elemento 0 es el último mensaje
    val showJumpButton by remember { derivedStateOf { listState.firstVisibleItemIndex > JUMP_BUTTON_THRESHOLD } }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(timeline) { item ->
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
                                isMention = item.isMention
                            )
                        }
                    }
                    is TimelineItem.PresenceGroup -> PresenceGroupRow(
                        group = item,
                        expanded = expandedGroups[item.key] == true,
                        onToggle = { expandedGroups[item.key] = expandedGroups[item.key] != true },
                        onMediaClick = onMediaClick,
                        showMediaPreviews = showMediaPreviews,
                        mainViewModel = mainViewModel
                    )
                    is TimelineItem.DateSeparator -> DateSeparatorRow(item.epochDay, today)
                    TimelineItem.NewMessages -> NewMessagesSeparatorRow()
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
