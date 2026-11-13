package com.jfaf.irc.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.jfaf.irc.domain.platform.AdHandle

/**
 * Piezas de la UI que dependen de la plataforma (anuncios e inicio de sesión de Google).
 * La UI común las pide por [LocalPlatformUi]; cada plataforma aporta su implementación.
 */
interface PlatformUi {
    /** Si la plataforma ofrece iniciar sesión con Google (en escritorio, no). */
    val supportsGoogleSignIn: Boolean

    /** Si es la versión de escritorio: ventana ancha con paneles fijos y foco automático en el campo de texto. */
    val isDesktop: Boolean get() = false

    /** Si el aviso de mensajes es un sonido de la app que se puede silenciar desde Ajustes (escritorio; en Android vibra). */
    val supportsSoundAlerts: Boolean get() = false

    @Composable
    fun GoogleSignInButton(onClick: () -> Unit)

    @Composable
    fun AdContent(ad: AdHandle)
}

/** Valor por defecto: sin anuncios y sin sesión de Google. */
object NoPlatformUi : PlatformUi {
    override val supportsGoogleSignIn = false

    @Composable
    override fun GoogleSignInButton(onClick: () -> Unit) {}

    @Composable
    override fun AdContent(ad: AdHandle) {}
}

val LocalPlatformUi = staticCompositionLocalOf<PlatformUi> { NoPlatformUi }
