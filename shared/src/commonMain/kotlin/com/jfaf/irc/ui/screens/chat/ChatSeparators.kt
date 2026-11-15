package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.jfaf.irc.data.prefs.MessageDensity
import com.jfaf.irc.domain.chat.DayLabel
import com.jfaf.irc.domain.chat.PresenceSummary
import com.jfaf.irc.domain.chat.TimelineItem
import com.jfaf.irc.domain.chat.model.MediaTypeEnum
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.cd_presence_collapse
import com.jfaf.irc.resources.cd_presence_expand
import com.jfaf.irc.resources.chat_date_format
import com.jfaf.irc.resources.chat_date_format_year
import com.jfaf.irc.resources.chat_date_today
import com.jfaf.irc.resources.chat_date_yesterday
import com.jfaf.irc.resources.chat_months
import com.jfaf.irc.resources.chat_new_messages
import com.jfaf.irc.resources.chat_presence_both
import com.jfaf.irc.resources.chat_presence_joined_many
import com.jfaf.irc.resources.chat_presence_joined_one
import com.jfaf.irc.resources.chat_presence_left_many
import com.jfaf.irc.resources.chat_presence_left_one
import com.jfaf.irc.resources.chat_presence_names_more
import com.jfaf.irc.resources.chat_presence_names_two
import com.jfaf.irc.ui.theme.IRCTheme
import com.jfaf.irc.ui.theme.ownNickColor
import com.jfaf.irc.ui.viewmodels.MainViewModel
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** Separador de día (UI-7): «Hoy», «Ayer» o la fecha. */
@Composable
fun DateSeparatorRow(epochDay: Long, todayEpochDay: Long) {
    val label = when (val day = DayLabel.of(epochDay, todayEpochDay)) {
        DayLabel.Today -> stringResource(Res.string.chat_date_today)
        DayLabel.Yesterday -> stringResource(Res.string.chat_date_yesterday)
        is DayLabel.Date -> {
            val month = stringArrayResource(Res.array.chat_months)[day.month - 1]
            if (day.showYear) stringResource(Res.string.chat_date_format_year, day.day, month, day.year)
            else stringResource(Res.string.chat_date_format, day.day, month)
        }
    }
    SeparatorLine(label, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.outlineVariant)
}

/** Línea «Nuevos mensajes» (UI-7): lo de debajo llegó mientras no mirabas la pestaña. */
@Composable
fun NewMessagesSeparatorRow() {
    val accent = IRCTheme.ownNickColor
    SeparatorLine(stringResource(Res.string.chat_new_messages), accent, accent.copy(alpha = 0.6f))
}

@Composable
private fun SeparatorLine(text: String, textColor: androidx.compose.ui.graphics.Color, lineColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = lineColor)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = lineColor)
    }
}

/** Entradas y salidas seguidas en una línea plegable (UI-6): «ana, kiko y 2 más entraron». */
@Composable
fun PresenceGroupRow(
    group: TimelineItem.PresenceGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMediaClick: (mediaUrl: String, mediaType: MediaTypeEnum) -> Unit,
    showMediaPreviews: Boolean,
    mainViewModel: MainViewModel,
    density: MessageDensity = MessageDensity.COMFORTABLE
) {
    val summary = PresenceSummary.of(group.messages)
    val joined = if (summary.joined.isNotEmpty()) {
        stringResource(
            if (summary.joined.size == 1) Res.string.chat_presence_joined_one else Res.string.chat_presence_joined_many,
            namesText(summary.joined)
        )
    } else null
    val left = if (summary.left.isNotEmpty()) {
        stringResource(
            if (summary.left.size == 1) Res.string.chat_presence_left_one else Res.string.chat_presence_left_many,
            namesText(summary.left)
        )
    } else null
    val text = when {
        joined != null && left != null -> stringResource(Res.string.chat_presence_both, joined, left)
        else -> joined ?: left ?: ""
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) Res.string.cd_presence_collapse else Res.string.cd_presence_expand
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            group.messages.forEach { message ->
                MessageRow(
                    message = message,
                    onMediaClick = onMediaClick,
                    showMediaPreviews = showMediaPreviews,
                    mainViewModel = mainViewModel,
                    showHeader = false,
                    density = density
                )
            }
        }
    }
}

@Composable
private fun namesText(names: List<String>): String = when (names.size) {
    1 -> names[0]
    2 -> stringResource(Res.string.chat_presence_names_two, names[0], names[1])
    else -> stringResource(Res.string.chat_presence_names_more, names[0], names[1], names.size - 2)
}
