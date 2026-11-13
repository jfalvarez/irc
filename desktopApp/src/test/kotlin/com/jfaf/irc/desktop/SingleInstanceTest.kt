package com.jfaf.irc.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SingleInstanceTest {

    @Test
    fun laSegundaInstanciaNoObtieneElBloqueoHastaQueLaPrimeraLoLibera() {
        val dir = Files.createTempDirectory("irc-lock-test").toFile()
        try {
            val first = SingleInstance.tryAcquire(dir)
            assertNotNull(first, "La primera instancia debe obtener el bloqueo")
            assertNull(SingleInstance.tryAcquire(dir), "La segunda no debe obtenerlo")

            first.close()
            val again = SingleInstance.tryAcquire(dir)
            assertNotNull(again, "Tras liberarlo debe poder obtenerse de nuevo")
            again.close()
        } finally {
            dir.deleteRecursively()
        }
    }
}
