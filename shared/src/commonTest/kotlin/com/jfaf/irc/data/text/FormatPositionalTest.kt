package com.jfaf.irc.data.text

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatPositionalTest {

    @Test
    fun numerosYOrden() {
        assertEquals("b-1-a", formatPositional("%2\$s-%3\$d-%1\$s", arrayOf<Any>("a", "b", 1)))
    }

    @Test
    fun argumentoQueFalta_seDejaElMarcador() {
        assertEquals("falta %2\$s", formatPositional("falta %2\$s", arrayOf<Any>("x")))
    }
}
