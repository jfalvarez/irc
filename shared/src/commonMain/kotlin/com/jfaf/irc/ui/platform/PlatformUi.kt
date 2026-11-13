package com.jfaf.irc.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.jfaf.irc.domain.chat.model.MediaTypeEnum
import com.jfaf.irc.domain.platform.AdHandle

/**
 * Piezas de la UI que dependen de la plataforma (anuncios, inicio de sesión de Google, imágenes y vídeo).
 * La UI común las pide por [LocalPlatformUi]; cada plataforma aporta su implementación.
 * Las de imagen y vídeo (Coil 2, Media3) se sustituyen en el paso 3.3.
 */
interface PlatformUi {
    /** Si la plataforma ofrece iniciar sesión con Google (en escritorio, no). */
    val supportsGoogleSignIn: Boolean

    @Composable
    fun GoogleSignInButton(onClick: () -> Unit)

    @Composable
    fun AdContent(ad: AdHandle)

    @Composable
    fun MediaPreview(mediaUrl: String, mediaType: MediaTypeEnum, modifier: Modifier)

    @Composable
    fun FullScreenMedia(mediaUrl: String, mediaType: MediaTypeEnum, onClose: () -> Unit)
}

/** Valor por defecto: sin anuncios, sin sesión de Google y sin vista previa de medios. */
object NoPlatformUi : PlatformUi {
    override val supportsGoogleSignIn = false

    @Composable
    override fun GoogleSignInButton(onClick: () -> Unit) {}

    @Composable
    override fun AdContent(ad: AdHandle) {}

    @Composable
    override fun MediaPreview(mediaUrl: String, mediaType: MediaTypeEnum, modifier: Modifier) {}

    @Composable
    override fun FullScreenMedia(mediaUrl: String, mediaType: MediaTypeEnum, onClose: () -> Unit) {}
}

val LocalPlatformUi = staticCompositionLocalOf<PlatformUi> { NoPlatformUi }
