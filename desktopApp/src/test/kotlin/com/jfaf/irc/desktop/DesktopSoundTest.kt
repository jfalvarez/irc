package com.jfaf.irc.desktop

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Aviso sonoro de escritorio: la lógica (ráfagas, síntesis y cuándo suena); el audio en sí se comprueba a mano. */
class DesktopSoundTest {

    private class CountingPlayer : SoundPlayer {
        var plays = 0
        override fun play() {
            plays++
        }
    }

    private fun ByteArray.sample(i: Int) = ((this[2 * i + 1].toInt() shl 8) or (this[2 * i].toInt() and 0xFF)).toShort().toInt()

    // --- Limitador de ráfagas
    @Test
    fun unaRafagaDeAvisosSuenaUnaSolaVez() {
        val player = CountingPlayer()
        var clock = 10_000L
        val limited = RateLimitedSoundPlayer(player, minIntervalMs = 1_000, now = { clock })

        repeat(5) { limited.play(); clock += 100 }

        assertEquals(1, player.plays)
    }

    @Test
    fun pasadoElIntervaloVuelveASonar() {
        val player = CountingPlayer()
        var clock = 10_000L
        val limited = RateLimitedSoundPlayer(player, minIntervalMs = 1_000, now = { clock })

        limited.play()
        clock += 1_000 // justo el intervalo mínimo: ya cuenta como otro aviso
        limited.play()

        assertEquals(2, player.plays)
    }

    @Test
    fun elPrimerAvisoSiempreSuena_aunqueElRelojEmpieceEnCero() {
        val player = CountingPlayer()
        RateLimitedSoundPlayer(player, minIntervalMs = 1_000, now = { 0L }).play()
        assertEquals(1, player.plays)
    }

    // --- Interruptor de Ajustes
    @Test
    fun silenciado_noSuenaYActivado_suena() {
        val player = CountingPlayer()
        var enabled = false
        val gated = PreferenceGatedSoundPlayer(player) { enabled }

        gated.play()
        assertEquals(0, player.plays)

        enabled = true
        gated.play()
        assertEquals(1, player.plays)
    }

    @Test
    fun unAvisoSilenciadoNoGastaElIntervaloDelLimitador() {
        val player = CountingPlayer()
        var clock = 10_000L
        var enabled = false
        // Mismo orden que en el módulo: primero el interruptor, luego el limitador
        val chain = PreferenceGatedSoundPlayer(RateLimitedSoundPlayer(player, 1_000, now = { clock })) { enabled }

        chain.play() // silenciado: el limitador ni lo ve
        clock += 100
        enabled = true
        chain.play() // 100 ms después, pero debe sonar

        assertEquals(1, player.plays)
    }

    // --- Síntesis
    @Test
    fun elSonidoTieneLaDuracionEsperada() {
        val samples = ToneSynth.pcm().size / 2
        val expected = 2 * (ToneSynth.SAMPLE_RATE * ToneSynth.NOTE_MS / 1000) + ToneSynth.SAMPLE_RATE * ToneSynth.GAP_MS / 1000
        assertEquals(expected, samples)
        assertTrue(samples * 1000 / ToneSynth.SAMPLE_RATE < 300, "Debe ser un aviso corto")
    }

    @Test
    fun elVolumenEsModeradoYNoSeSatura() {
        val pcm = ToneSynth.pcm()
        val peak = (0 until pcm.size / 2).maxOf { abs(pcm.sample(it)) }
        assertTrue(peak > 8_000, "Debe oírse (pico $peak)")
        assertTrue(peak < Short.MAX_VALUE / 2, "No debe ser estridente (pico $peak)")
    }

    @Test
    fun cadaNotaEmpiezaYAcabaEnSilencioParaEvitarChasquidos() {
        val pcm = ToneSynth.pcm()
        val note = ToneSynth.SAMPLE_RATE * ToneSynth.NOTE_MS / 1000
        val gap = ToneSynth.SAMPLE_RATE * ToneSynth.GAP_MS / 1000
        assertEquals(0, pcm.sample(0))
        assertEquals(0, pcm.sample(note - 1))
        assertEquals(0, pcm.sample(note + gap))
        assertEquals(0, pcm.sample(2 * note + gap - 1))
        // y el hueco entre notas es silencio
        assertTrue((note until note + gap).all { pcm.sample(it) == 0 })
    }

    // --- Cuándo suena
    @Test
    fun hapticsDeEscritorioReproduceElSonido() {
        val player = CountingPlayer()
        DesktopHaptics(player).vibrate()
        assertEquals(1, player.plays)
    }

    @Test
    fun elAvisoEnSegundoPlanoSuenaAunqueNoHayaBandeja() {
        val player = CountingPlayer()
        val notifier = DesktopNotifier(player) // sin attach(): sin bandeja

        notifier.showPrivateMessage("Ana", "hola", "Ana")
        assertEquals(1, player.plays)

        notifier.showFriendOnline("Luis")
        assertEquals(2, player.plays)
    }
}
