package com.jfaf.irc.ads

import android.content.Context
import com.jfaf.irc.log.Log
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.jfaf.irc.domain.platform.AdHandle
import com.jfaf.irc.domain.platform.AdProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Anuncio nativo de AdMob; lo desenvuelve `MessagesList` para pintarlo. */
class AdMobAd(val nativeAd: NativeAd) : AdHandle

class NativeAdManager(
    private val context: Context
) : AdProvider {

    private val _currentAd = MutableStateFlow<AdHandle?>(null)
    override val currentAd: StateFlow<AdHandle?> = _currentAd.asStateFlow()

    override fun load() {
        val adLoader = AdLoader.Builder(context, "ca-app-pub-3940256099942544/2247696110") // TEST NATIVE ID
            .forNativeAd { ad: NativeAd ->
                _currentAd.value = AdMobAd(ad)
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(adError: com.google.android.gms.ads.LoadAdError) {
                    Log.e("NativeAdManager", "Ad failed to load: ${adError.message}")
                    _currentAd.value = null
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }
}
