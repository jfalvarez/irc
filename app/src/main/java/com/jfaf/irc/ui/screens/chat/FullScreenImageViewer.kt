package com.jfaf.irc.ui.screens.chat

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.jfaf.irc.R

@Composable
fun FullScreenImageViewer(imageUrl: String, onClose: () -> Unit) {
    Log.d("FullScreenImageViewer", "Displaying image. URL: $imageUrl")

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        var imagePainterState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl, 
                contentDescription = stringResource(R.string.cd_full_screen_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(8.dp))
                    .graphicsLayer { 
                        alpha = if (imagePainterState is AsyncImagePainter.State.Success) 1f else 0f
                    }
                    .clickable( 
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* No hacer nada para evitar cerrar el diálogo al tocar la imagen */ }
                    ),
                onState = { state ->
                    Log.d("FullScreenImageViewer", "AsyncImage State changed: $state for URL: $imageUrl") 
                    imagePainterState = state 
                }
            )

            when (val currentState = imagePainterState) {
                is AsyncImagePainter.State.Loading -> {
                    Log.d("FullScreenImageViewer", "Showing loading indicator because painterState is Loading.")
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                }
                is AsyncImagePainter.State.Error -> {
                    Log.e("FullScreenImageViewer", "Showing error message. Error: ${currentState.result.throwable}")
                    Text(
                        text = stringResource(R.string.error_loading_image),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                            .padding(16.dp)
                    )
                }
                is AsyncImagePainter.State.Success -> {
                    Log.d("FullScreenImageViewer", "Image successfully loaded. DataSource: ${currentState.result.dataSource}")
                }
                is AsyncImagePainter.State.Empty -> {
                     Log.d("FullScreenImageViewer", "Painter state is Empty. Showing loading indicator as a fallback.")
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
