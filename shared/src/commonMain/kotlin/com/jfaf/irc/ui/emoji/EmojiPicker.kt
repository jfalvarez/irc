package com.jfaf.irc.ui.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.emoji_category_recent
import com.jfaf.irc.ui.theme.IRCTheme
import org.jetbrains.compose.resources.stringResource

private const val RECENT_ICON = "🕒" // 🕒

private val CELL_SIZE = 40.dp
private val PICKER_WIDTH = 360.dp
private val GRID_HEIGHT = 220.dp

/** El selector se coloca justo encima de su ancla (el campo de texto), sin salirse de la ventana. */
private object AboveAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val y = (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/**
 * Selector de emoticonos: pestañas por categoría (con «Recientes» delante si hay) y una cuadrícula.
 * Se coloca sobre el campo de texto y no le quita el foco; [onDismiss] se llama al pulsar fuera (el campo cierra el
 * selector con Esc).
 */
@Composable
fun EmojiPicker(
    recents: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val categories = remember(recents) {
        buildList {
            if (recents.isNotEmpty()) add(EmojiCategory(RECENT_ICON, Res.string.emoji_category_recent, recents))
            addAll(EmojiCatalog.categories)
        }
    }
    var selected by remember { mutableStateOf(0) }
    val current = categories[selected.coerceIn(0, categories.lastIndex)]

    // No roba el foco al campo de texto: si lo perdiera, Compose colapsaría la selección y no se podría sustituir lo
    // seleccionado; así además se sigue escribiendo con el selector abierto. Por eso Esc lo gestiona el campo.
    Popup(
        popupPositionProvider = AboveAnchorPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = IRCTheme.dropdownMenuContainerOpaque,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.width(PICKER_WIDTH)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    categories.forEachIndexed { index, category ->
                        val name = stringResource(category.name)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(CELL_SIZE)
                                .background(
                                    if (category === current) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .semantics { contentDescription = name }
                                .focusProperties { canFocus = false }
                                .clickable { selected = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(category.icon, fontSize = 20.sp)
                        }
                    }
                }
                Text(
                    text = stringResource(current.name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(CELL_SIZE),
                    modifier = Modifier.fillMaxWidth().height(GRID_HEIGHT).padding(horizontal = 4.dp)
                ) {
                    items(current.emojis, key = { it }) { emoji ->
                        Box(
                            modifier = Modifier.size(CELL_SIZE).focusProperties { canFocus = false }.clickable { onPick(emoji) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}
