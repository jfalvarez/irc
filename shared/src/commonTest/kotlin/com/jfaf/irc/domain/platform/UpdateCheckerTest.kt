package com.jfaf.irc.domain.platform

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class UpdateCheckerTest {
    @Test fun versionMenorQueLaMinima_pideActualizar() = assertTrue(isUpdateRequired(1, 2))
    @Test fun versionIgualOMayor_noPide() { assertFalse(isUpdateRequired(2, 2)); assertFalse(isUpdateRequired(3, 2)) }
    @Test fun versionDesconocida_noPide() = assertFalse(isUpdateRequired(UNKNOWN_VERSION, 5))
}
