package com.jfaf.irc.domain.privacy

/**
 * Qué permite el usuario a la analítica y a los anuncios, con los cuatro tipos del modo de
 * consentimiento v2 de Google (`ad_storage`, `ad_user_data`, `ad_personalization`, `analytics_storage`).
 */
data class TrackingConsent(
    val adStorage: Boolean,
    val adUserData: Boolean,
    val adPersonalization: Boolean,
    val analyticsStorage: Boolean,
) {
    companion object {
        val ALL_GRANTED = TrackingConsent(adStorage = true, adUserData = true, adPersonalization = true, analyticsStorage = true)
        val ALL_DENIED = TrackingConsent(adStorage = false, adUserData = false, adPersonalization = false, analyticsStorage = false)

        /**
         * Traduce lo que el formulario de consentimiento (CMP con IAB TCF v2.2) deja guardado.
         *
         * @param gdprApplies valor de `IABTCF_gdprApplies`: 1 si aplica el RGPD, 0 si no, `null` si
         *   aún no hay respuesta (sin red, primer arranque…): entonces se deniega todo.
         * @param purposeConsents valor de `IABTCF_PurposeConsents`: un carácter por propósito
         *   (el primero es el propósito 1), `'1'` si el usuario lo acepta.
         *
         * Correspondencia (la de Google para `ad_*`; `analytics_storage` no la fija el TCF):
         * - `ad_storage`: propósito 1 (guardar o leer información en el dispositivo).
         * - `ad_user_data`: propósitos 1 y 7 (medir el rendimiento de los anuncios).
         * - `ad_personalization`: propósitos 3 y 4 (perfiles y anuncios personalizados).
         * - `analytics_storage`: propósitos 1 y 8 (medir el rendimiento del contenido).
         */
        fun fromTcf(gdprApplies: Int?, purposeConsents: String?): TrackingConsent {
            if (gdprApplies == 0) return ALL_GRANTED
            if (gdprApplies != 1) return ALL_DENIED
            fun purpose(n: Int) = purposeConsents?.getOrNull(n - 1) == '1'
            return TrackingConsent(
                adStorage = purpose(1),
                adUserData = purpose(1) && purpose(7),
                adPersonalization = purpose(3) && purpose(4),
                analyticsStorage = purpose(1) && purpose(8),
            )
        }
    }
}
