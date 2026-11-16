package com.jfaf.irc.domain.terms

// Versiones: 1 = las primeras (2026-11-14); 2 = añade que la vista previa de enlaces visita la web y le revela la IP (UI-31).
/**
 * Condiciones de uso que el usuario acepta antes de chatear (lo exige Google Play a las apps con
 * contenido de usuarios). El texto está en los recursos (`terms_text`); si cambia en algo importante,
 * se sube [CURRENT_VERSION] y se vuelven a pedir.
 */
object TermsOfUse {
    const val CURRENT_VERSION = 2

    /** Edad mínima que piden las condiciones (y la política de privacidad y la clasificación de Play). */
    const val MINIMUM_AGE = 18

    /** Si la versión aceptada [acceptedVersion] (null si nunca) cubre las condiciones actuales. */
    fun isAccepted(acceptedVersion: Int?): Boolean = (acceptedVersion ?: 0) >= CURRENT_VERSION
}
