package com.jfaf.irc.service

import com.jfaf.irc.data.model.IrcLineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PmNotificationPolicyTest {

    private fun msg(line: String) = IrcLineParser.parse(line)!!

    private val pm = msg(":Ana!u@h PRIVMSG ElTester :hola")
    private val channel = msg(":Ana!u@h PRIVMSG #chat :hola")

    @Test
    fun notificaPmEnSegundoPlano() {
        assertTrue(PmNotificationPolicy.shouldNotify(pm, emptySet(), appInForeground = false))
    }

    @Test
    fun noNotificaEnPrimerPlano_niCanales_niOtrosComandos() {
        assertFalse(PmNotificationPolicy.shouldNotify(pm, emptySet(), appInForeground = true))
        assertFalse(PmNotificationPolicy.shouldNotify(channel, emptySet(), appInForeground = false))
        assertFalse(PmNotificationPolicy.shouldNotify(msg(":Ana!u@h NOTICE ElTester :x"), emptySet(), false))
    }

    @Test
    fun ignoradosNoNotificanSinImportarMayusculas() {
        assertFalse(PmNotificationPolicy.shouldNotify(pm, setOf("ANA"), appInForeground = false))
        assertTrue(PmNotificationPolicy.shouldNotify(pm, setOf("otro"), appInForeground = false))
    }

    @Test
    fun idPorConversacion_estableYDistintoEntrePersonas() {
        val ana = PmNotificationPolicy.notificationId(PmNotificationPolicy.conversationKey(pm))
        val anaOtraVez = PmNotificationPolicy.notificationId(PmNotificationPolicy.conversationKey(msg(":ANA!x@y PRIVMSG ElTester :otra")))
        val bob = PmNotificationPolicy.notificationId(PmNotificationPolicy.conversationKey(msg(":Bob!u@h PRIVMSG ElTester :hola")))
        assertEquals(ana, anaOtraVez)
        assertNotEquals(ana, bob)
        assertTrue(ana >= 1000)
    }
}
