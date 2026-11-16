package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jfaf.irc.domain.notifications.ChannelNotificationLevel
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.button_cancel
import com.jfaf.irc.resources.channel_notifications_hint
import com.jfaf.irc.resources.channel_notifications_level_default
import com.jfaf.irc.resources.channel_notifications_level_all
import com.jfaf.irc.resources.channel_notifications_level_mentions
import com.jfaf.irc.resources.channel_notifications_level_muted
import com.jfaf.irc.resources.channel_notifications_title
import com.jfaf.irc.ui.theme.IRCTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** El texto corto de un nivel («Solo menciones»), para el menú y el diálogo. */
fun channelNotificationLabel(level: ChannelNotificationLevel): StringResource = when (level) {
    ChannelNotificationLevel.ALL -> Res.string.channel_notifications_level_all
    ChannelNotificationLevel.MENTIONS -> Res.string.channel_notifications_level_mentions
    ChannelNotificationLevel.MUTED -> Res.string.channel_notifications_level_muted
}

/**
 * Diálogo para elegir cuánto avisa un canal con la app en segundo plano (UI-32). Elegir un nivel lo aplica y cierra.
 * La primera opción, «Predeterminado (…)», quita el nivel propio del canal: pasa a seguir el de Ajustes (UI-34).
 *
 * @param override el nivel propio del canal; null si sigue el predeterminado.
 * @param defaultLevel el nivel por defecto de Ajustes.
 */
@Composable
fun ChannelNotificationsDialog(
    channel: String,
    override: ChannelNotificationLevel?,
    defaultLevel: ChannelNotificationLevel,
    onSelect: (ChannelNotificationLevel?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.channel_notifications_title, channel), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    stringResource(Res.string.channel_notifications_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(modifier = Modifier.selectableGroup()) {
                    // null = seguir el nivel por defecto
                    (listOf<ChannelNotificationLevel?>(null) + ChannelNotificationLevel.entries).forEach { level ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = level == override, role = Role.RadioButton, onClick = { onSelect(level) })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            RadioButton(selected = level == override, onClick = null)
                            Text(
                                if (level == null) {
                                    stringResource(Res.string.channel_notifications_level_default, stringResource(channelNotificationLabel(defaultLevel)))
                                } else {
                                    stringResource(channelNotificationLabel(level))
                                },
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.button_cancel), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            }
        },
        containerColor = IRCTheme.dialogContainerOpaque
    )
}
