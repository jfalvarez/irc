package com.jfaf.irc.desktop

import androidx.compose.runtime.Composable
import com.jfaf.irc.domain.platform.AdHandle
import com.jfaf.irc.ui.platform.PlatformUi

/**
 * [PlatformUi] de escritorio: ventana ancha con paneles fijos y foco automático en el campo de texto;
 * sin anuncios.
 */
class DesktopPlatformUi : PlatformUi {
    override val isDesktop = true
    override val supportsSoundAlerts = true

    @Composable
    override fun AdContent(ad: AdHandle) {}
}
