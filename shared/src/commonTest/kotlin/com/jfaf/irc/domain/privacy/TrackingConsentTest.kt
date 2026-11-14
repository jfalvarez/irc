package com.jfaf.irc.domain.privacy

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackingConsentTest {

    @Test
    fun sinRespuestaDelFormulario_seDeniegaTodo() {
        assertEquals(TrackingConsent.ALL_DENIED, TrackingConsent.fromTcf(gdprApplies = null, purposeConsents = null))
        assertEquals(TrackingConsent.ALL_DENIED, TrackingConsent.fromTcf(gdprApplies = null, purposeConsents = "1111111111"))
    }

    @Test
    fun fueraDelRgpd_sePermiteTodo() {
        assertEquals(TrackingConsent.ALL_GRANTED, TrackingConsent.fromTcf(gdprApplies = 0, purposeConsents = null))
    }

    @Test
    fun conRgpdYTodoAceptado_sePermiteTodo() {
        assertEquals(TrackingConsent.ALL_GRANTED, TrackingConsent.fromTcf(gdprApplies = 1, purposeConsents = "11111111111"))
    }

    @Test
    fun conRgpdYTodoRechazado_seDeniegaTodo() {
        assertEquals(TrackingConsent.ALL_DENIED, TrackingConsent.fromTcf(gdprApplies = 1, purposeConsents = "00000000000"))
        assertEquals(TrackingConsent.ALL_DENIED, TrackingConsent.fromTcf(gdprApplies = 1, purposeConsents = ""))
        assertEquals(TrackingConsent.ALL_DENIED, TrackingConsent.fromTcf(gdprApplies = 1, purposeConsents = null))
    }

    @Test
    fun sinElProposito1_nadaQueGuardeEnElDispositivo() {
        // Todo aceptado menos el propósito 1: sólo queda la personalización (propósitos 3 y 4).
        val consent = TrackingConsent.fromTcf(gdprApplies = 1, purposeConsents = "01111111111")
        assertEquals(TrackingConsent(adStorage = false, adUserData = false, adPersonalization = true, analyticsStorage = false), consent)
    }

    @Test
    fun cadaTipoDependeDeSusPropositos() {
        // Propósitos 1 y 8: analítica y almacenamiento, sin anuncios personalizados ni medición de anuncios.
        assertEquals(
            TrackingConsent(adStorage = true, adUserData = false, adPersonalization = false, analyticsStorage = true),
            TrackingConsent.fromTcf(gdprApplies = 1, purposeConsents = "10000001")
        )
        // Propósitos 1 y 7.
        assertEquals(
            TrackingConsent(adStorage = true, adUserData = true, adPersonalization = false, analyticsStorage = false),
            TrackingConsent.fromTcf(gdprApplies = 1, purposeConsents = "1000001")
        )
        // Sólo el 3 sin el 4: no hay personalización.
        assertEquals(
            TrackingConsent(adStorage = true, adUserData = false, adPersonalization = false, analyticsStorage = false),
            TrackingConsent.fromTcf(gdprApplies = 1, purposeConsents = "101")
        )
    }

    @Test
    fun valorDeGdprDesconocido_seDeniegaTodo() {
        assertEquals(TrackingConsent.ALL_DENIED, TrackingConsent.fromTcf(gdprApplies = 2, purposeConsents = "11111111111"))
    }
}
