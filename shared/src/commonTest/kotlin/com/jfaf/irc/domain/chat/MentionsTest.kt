package com.jfaf.irc.domain.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MentionsTest {
    @Test
    fun detectaElNickComoPalabra() {
        assertTrue(Mentions.isMention("hola ana", "ana"))
        assertTrue(Mentions.isMention("ana: ven", "ana"))
        assertTrue(Mentions.isMention("ven, ana.", "ana"))
        assertTrue(Mentions.isMention("ana", "ana"))
        assertTrue(Mentions.isMention("hola @ana!", "ana"))
        assertTrue(Mentions.isMention("(ana)", "ana"))
    }

    @Test
    fun noDistingueMayusculas() {
        assertTrue(Mentions.isMention("HOLA ANA", "ana"))
        assertTrue(Mentions.isMention("hola ana", "Ana"))
    }

    @Test
    fun ignoraElPrefijoDeModoDelNick() {
        assertTrue(Mentions.isMention("hola ana", "@ana"))
    }

    @Test
    fun noEsMencionSiFormaParteDeOtraPalabraONick() {
        assertFalse(Mentions.isMention("banana", "ana"))
        assertFalse(Mentions.isMention("ana2", "ana"))
        assertFalse(Mentions.isMention("ana_", "ana"))
        assertFalse(Mentions.isMention("mana", "ana"))
        assertFalse(Mentions.isMention("ana-b", "ana"))
    }

    @Test
    fun encuentraLaSegundaApariciónSiLaPrimeraEsParteDeOtraPalabra() {
        assertTrue(Mentions.isMention("banana, ana", "ana"))
    }

    @Test
    fun nicksConCaracteresEspeciales() {
        assertTrue(Mentions.isMention("hola [x]_y", "[x]_y"))
        assertTrue(Mentions.isMention("hola kiko|away!", "kiko|away"))
        assertFalse(Mentions.isMention("hola kiko|away2", "kiko|away"))
    }

    @Test
    fun nickVacioNoMenciona() {
        assertFalse(Mentions.isMention("hola", ""))
        assertFalse(Mentions.isMention("hola", "@"))
    }
}
