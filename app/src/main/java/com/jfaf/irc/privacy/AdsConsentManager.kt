package com.jfaf.irc.privacy

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import com.jfaf.irc.BuildConfig
import com.jfaf.irc.domain.privacy.TrackingConsent
import com.jfaf.irc.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Consentimiento RGPD con el formulario de Google (UMP, IAB TCF v2.2), antes de anuncios y analítica.
 *
 * - Anuncios: no se inicializa AdMob ni se pide ningún anuncio hasta que [canRequestAds] es `true`.
 * - Analítica: arranca desactivada y con todo denegado (meta-datos del manifiesto); al resolverse
 *   el consentimiento se aplica [TrackingConsent.fromTcf] con lo que el formulario dejó guardado.
 * - [privacyOptionsRequired]: si el usuario debe poder cambiar su elección desde Ajustes
 *   (lo exige Google cuando ha visto el formulario).
 *
 * El formulario y su texto se configuran en AdMob → Privacidad y mensajes → RGPD.
 */
class AdsConsentManager(
    private val context: Context,
    private val analytics: FirebaseAnalytics,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val TAG = "AdsConsent"
        const val TCF_GDPR_APPLIES = "IABTCF_gdprApplies"
        const val TCF_PURPOSE_CONSENTS = "IABTCF_PurposeConsents"
        /** Dispositivos de prueba de AdMob (ven anuncios de prueba aunque el ID sea real). */
        val AD_TEST_DEVICE_IDS = listOf("CC7C862030BF02013922527B26AC1285")
    }

    private val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val adsInitialized = AtomicBoolean(false)

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /**
     * Pide el estado del consentimiento y, si hace falta, muestra el formulario. Llamar en cada
     * `onCreate` de la actividad. Con el consentimiento de una sesión anterior se sigue sin esperar.
     */
    fun gatherConsent(activity: Activity) {
        consentInformation.requestConsentInfoUpdate(
            activity,
            requestParameters(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    error?.let { logError("formulario", it) }
                    onConsentUpdated()
                }
            },
            { error ->
                logError("actualizar estado", error)
                onConsentUpdated()
            }
        )
        // Lo guardado de la sesión anterior vale mientras llega la respuesta.
        onConsentUpdated()
    }

    /** Vuelve a mostrar el formulario para cambiar la elección (entrada de Ajustes). */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            error?.let { logError("opciones de privacidad", it) }
            onConsentUpdated()
        }
    }

    private fun onConsentUpdated() {
        _privacyOptionsRequired.value = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        applyAnalyticsConsent()
        if (consentInformation.canRequestAds()) {
            initializeAdsOnce()
            _canRequestAds.value = true
        }
    }

    private fun applyAnalyticsConsent() {
        val consent = if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED) {
            TrackingConsent.ALL_GRANTED
        } else {
            val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            val gdprApplies = runCatching { prefs.getInt(TCF_GDPR_APPLIES, -1) }.getOrDefault(-1).takeIf { it >= 0 }
            TrackingConsent.fromTcf(gdprApplies, prefs.getString(TCF_PURPOSE_CONSENTS, null))
        }
        fun status(granted: Boolean) = if (granted) ConsentStatus.GRANTED else ConsentStatus.DENIED
        analytics.setConsent(
            mapOf(
                ConsentType.AD_STORAGE to status(consent.adStorage),
                ConsentType.AD_USER_DATA to status(consent.adUserData),
                ConsentType.AD_PERSONALIZATION to status(consent.adPersonalization),
                ConsentType.ANALYTICS_STORAGE to status(consent.analyticsStorage),
            )
        )
        analytics.setAnalyticsCollectionEnabled(consent.analyticsStorage)
        Log.i(TAG, "Consentimiento aplicado: $consent")
    }

    private fun initializeAdsOnce() {
        if (!adsInitialized.compareAndSet(false, true)) return
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(AD_TEST_DEVICE_IDS).build()
        )
        // Google recomienda inicializar fuera del hilo principal.
        scope.launch(Dispatchers.IO) { MobileAds.initialize(context) {} }
    }

    private fun requestParameters(): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        // En debug, con el id del dispositivo (lo escribe UMP en Logcat), se prueba como si se estuviera en la UE.
        if (BuildConfig.DEBUG && BuildConfig.UMP_TEST_DEVICE_ID.isNotBlank()) {
            builder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(context)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE_ID)
                    .build()
            )
        }
        return builder.build()
    }

    private fun logError(step: String, error: FormError) {
        Log.w(TAG, "Consentimiento ($step): ${error.errorCode} ${error.message}")
    }
}
