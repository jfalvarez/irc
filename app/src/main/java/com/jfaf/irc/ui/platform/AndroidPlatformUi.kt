package com.jfaf.irc.ui.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jfaf.irc.ads.AdMobAd
import com.jfaf.irc.domain.platform.AdHandle
import com.jfaf.irc.privacy.AdsConsentManager
import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.settings_privacy_options
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Implementación Android de [PlatformUi]: AdMob (con su consentimiento). */
object AndroidPlatformUi : PlatformUi {
    @Composable
    override fun AdContent(ad: AdHandle) {
        (ad as? AdMobAd)?.let { NativeAdMessage(nativeAd = it.nativeAd) }
    }

    @Composable
    override fun PrivacyOptionsItem() {
        val consent: AdsConsentManager = koinInject()
        val required by consent.privacyOptionsRequired.collectAsState()
        val activity = LocalContext.current.findActivity()
        if (!required || activity == null) return
        Text(
            text = stringResource(Res.string.settings_privacy_options),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { consent.showPrivacyOptions(activity) }
                .padding(vertical = 16.dp)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
