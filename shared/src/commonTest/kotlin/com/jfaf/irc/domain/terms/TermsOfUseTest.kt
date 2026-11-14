package com.jfaf.irc.domain.terms

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TermsOfUseTest {

    @Test
    fun nuncaAceptadas_hayQuePedirlas() {
        assertFalse(TermsOfUse.isAccepted(null))
        assertFalse(TermsOfUse.isAccepted(0))
    }

    @Test
    fun versionActualOPosterior_aceptadas() {
        assertTrue(TermsOfUse.isAccepted(TermsOfUse.CURRENT_VERSION))
        assertTrue(TermsOfUse.isAccepted(TermsOfUse.CURRENT_VERSION + 1))
    }

    @Test
    fun versionAnterior_hayQueVolverAPedirlas() {
        assertFalse(TermsOfUse.isAccepted(TermsOfUse.CURRENT_VERSION - 1))
    }
}
