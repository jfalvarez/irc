package com.jfaf.irc.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.drawer_search_hint
import com.jfaf.irc.ui.platform.LocalPlatformUi
import org.jetbrains.compose.resources.stringResource

/**
 * Fila «Buscar…» arriba del panel de pestañas (UI-30): abre el selector rápido. En escritorio lleva a la derecha la
 * etiqueta del atajo (`Ctrl+K`), que es la forma de que el usuario lo descubra sin tener que adivinarlo.
 */
@Composable
fun DrawerSearchRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shortcut = if (LocalPlatformUi.current.isDesktop) QUICK_SWITCH_SHORTCUT else null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(Res.string.drawer_search_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (shortcut != null) KeyCap(shortcut)
    }
}

/** La etiqueta de una tecla o atajo (`Ctrl+K`): texto pequeño con borde fino, como una tecla. */
@Composable
fun KeyCap(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
