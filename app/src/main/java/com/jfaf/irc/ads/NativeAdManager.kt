package com.jfaf.irc.ads

import android.content.Context
import com.jfaf.irc.log.Log
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.jfaf.irc.domain.platform.AdHandle
import com.jfaf.irc.domain.platform.AdProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Anuncio nativo de AdMob; lo desenvuelve `MessagesList` para pintarlo. */
class AdMobAd(val nativeAd: NativeAd) : AdHandle

/**
 * Anuncios nativos de AdMob. No pide ninguno hasta que hay consentimiento ([canRequestAds], de
 * `AdsConsentManager`): un [load] anterior queda pendiente y se hace en cuanto lo hay.
 */
class NativeAdManager(
    private val context: Context,
    private val canRequestAds: StateFlow<Boolean>,
    scope: CoroutineScope,
) : AdProvider {

    private val _currentAd = MutableStateFlow<AdHandle?>(null)
    override val currentAd: StateFlow<AdHandle?> = _currentAd.asStateFlow()

    /** Sólo se toca en el hilo principal (la UI llama a [load] y el colector va en Main). */
    private var pendingLoad = false

    init {
        scope.launch(Dispatchers.Main) {
            canRequestAds.first { it }
            if (pendingLoad) {
                pendingLoad = false
                loadNow()
            }
        }
    }

    override fun load() {
        if (canRequestAds.value) loadNow() else pendingLoad = true
    }

    private fun loadNow() {
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
