package com.jfaf.irc.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeAdManager @Inject constructor() {

    private val _nativeAd = MutableStateFlow<NativeAd?>(null)
    val nativeAd: StateFlow<NativeAd?> = _nativeAd

    fun loadAd(context: Context) {
        val adLoader = AdLoader.Builder(context, "ca-app-pub-3940256099942544/2247696110") // TEST NATIVE ID
            .forNativeAd { ad: NativeAd ->
                _nativeAd.value = ad
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(adError: com.google.android.gms.ads.LoadAdError) {
                    Log.e("NativeAdManager", "Ad failed to load: ${adError.message}")
                    _nativeAd.value = null
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }
}