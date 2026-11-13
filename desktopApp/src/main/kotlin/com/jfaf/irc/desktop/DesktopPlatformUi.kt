package com.jfaf.irc.desktop

import androidx.compose.runtime.Composable
import com.jfaf.irc.domain.platform.AdHandle
import com.jfaf.irc.ui.platform.PlatformUi

/**
 * [PlatformUi] de escritorio: ventana ancha con paneles fijos y foco automático en el campo de texto;
 * sin anuncios ni inicio de sesión de Google (decisión 0.1).
 */
object DesktopPlatformUi : PlatformUi {
    override val supportsGoogleSignIn = false
    override val isDesktop = true

    @Composable
    override fun GoogleSignInButton(onClick: () -> Unit) {}

    @Composable
    override fun AdContent(ad: AdHandle) {}
}
