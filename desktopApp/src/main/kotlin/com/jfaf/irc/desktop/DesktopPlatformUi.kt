package com.jfaf.irc.desktop

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.jfaf.irc.domain.platform.AdHandle
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.account_sign_in_google
import com.jfaf.irc.ui.platform.PlatformUi
import org.jetbrains.compose.resources.stringResource

/**
 * [PlatformUi] de escritorio: ventana ancha con paneles fijos y foco automático en el campo de texto;
 * sin anuncios. Ofrece iniciar sesión con Google sólo si [supportsGoogleSignIn] (hay configuración, ver
 * [DesktopGoogleConfig]).
 */
class DesktopPlatformUi(override val supportsGoogleSignIn: Boolean = false) : PlatformUi {
    override val isDesktop = true
    override val supportsSoundAlerts = true

    @Composable
    override fun GoogleSignInButton(onClick: () -> Unit) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text(stringResource(Res.string.account_sign_in_google))
        }
    }

    @Composable
    override fun AdContent(ad: AdHandle) {}
}
