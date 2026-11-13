package com.jfaf.irc.ui.platform

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jfaf.irc.R
import com.jfaf.irc.ads.AdMobAd
import com.jfaf.irc.domain.chat.model.MediaTypeEnum
import com.jfaf.irc.domain.platform.AdHandle
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.account_sign_in_google
import org.jetbrains.compose.resources.stringResource

/** Implementación Android de [PlatformUi]: AdMob, Google Sign-In, Coil 2 y Media3. */
object AndroidPlatformUi : PlatformUi {
    override val supportsGoogleSignIn = true

    @Composable
    override fun GoogleSignInButton(onClick: () -> Unit) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_google_logo),
                contentDescription = "Google Logo",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.account_sign_in_google))
        }
    }

    @Composable
    override fun AdContent(ad: AdHandle) {
        (ad as? AdMobAd)?.let { NativeAdMessage(nativeAd = it.nativeAd) }
    }

    @Composable
    override fun MediaPreview(mediaUrl: String, mediaType: MediaTypeEnum, modifier: Modifier) {
        com.jfaf.irc.ui.platform.MediaPreview(modifier = modifier, mediaUrl = mediaUrl, mediaType = mediaType)
    }

    @Composable
    override fun FullScreenMedia(mediaUrl: String, mediaType: MediaTypeEnum, onClose: () -> Unit) {
        FullScreenImageViewer(mediaUrl = mediaUrl, mediaType = mediaType, onClose = onClose)
    }
}
