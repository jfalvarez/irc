package com.jfaf.irc.desktop

import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.domain.platform.Haptics
import com.jfaf.irc.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import java.awt.Toolkit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/** Reproduce el aviso sonoro de un mensaje. Detrás de una interfaz para probar la lógica sin audio. */
fun interface SoundPlayer {
    fun play()
}

/**
 * Ignora los avisos que llegan antes de [minIntervalMs] desde el último que sonó: una ráfaga de mensajes
 * (al reentrar en un canal, por ejemplo) no debe encadenar sonidos.
 */
class RateLimitedSoundPlayer(
    private val delegate: SoundPlayer,
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val now: () -> Long = System::currentTimeMillis
) : SoundPlayer {

    private var lastPlayedAt: Long? = null

    @Synchronized
    override fun play() {
        val current = now()
        val last = lastPlayedAt
        if (last != null && current - last < minIntervalMs) return
        lastPlayedAt = current
        delegate.play()
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS = 1_000L
    }
}

/**
 * El interruptor "Sonido de aviso" de Ajustes, siempre al día y de lectura inmediata (el sonido se decide en el
 * hilo de la interfaz). Se lee el valor guardado antes de exponerlo: un aviso en los primeros instantes no debe
 * saltarse un silencio ya elegido.
 */
class SoundAlertsSetting(preferences: UserPreferencesRepository, scope: CoroutineScope) {
    val enabled: StateFlow<Boolean> = preferences.soundAlertsFlow.stateIn(
        scope,
        SharingStarted.Eagerly,
        runBlocking { preferences.soundAlertsFlow.first() }
    )
}

/**
 * Silencia el sonido cuando [enabled] devuelve false (el interruptor de Ajustes). Va por fuera de
 * [RateLimitedSoundPlayer]: un aviso silenciado no consume el intervalo del limitador.
 */
class PreferenceGatedSoundPlayer(
    private val delegate: SoundPlayer,
    private val enabled: () -> Boolean
) : SoundPlayer {
    override fun play() {
        if (enabled()) delegate.play()
    }
}

/**
 * El "vibrar" de escritorio es un sonido corto. `App` decide cuándo (privado a otra pestaña o amigo conectado, con
 * la ventana a la vista); en segundo plano suena [DesktopNotifier] al mostrar el aviso.
 */
class DesktopHaptics(private val sound: SoundPlayer) : Haptics {
    override fun vibrate() = sound.play()
}

/**
 * Dos notas suaves (La5 y Re6) sintetizadas en código: no hay archivo de audio que empaquetar. Un pequeño fundido
 * al principio y al final de cada nota evita los chasquidos.
 */
object ToneSynth {
    const val SAMPLE_RATE = 44_100
    const val NOTE_MS = 110
    const val GAP_MS = 20
    private const val FADE_MS = 8
    private const val AMPLITUDE = 0.35 // volumen moderado: es un aviso, no una alarma
    private val NOTES_HZ = doubleArrayOf(880.0, 1174.66)

    /** PCM de 16 bits con signo, mono, little-endian. */
    val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)

    fun pcm(): ByteArray {
        val noteSamples = SAMPLE_RATE * NOTE_MS / 1000
        val gapSamples = SAMPLE_RATE * GAP_MS / 1000
        val fadeSamples = (SAMPLE_RATE * FADE_MS / 1000).toDouble()
        val total = NOTES_HZ.size * noteSamples + (NOTES_HZ.size - 1) * gapSamples
        val out = ByteArray(total * 2)
        var index = 0
        NOTES_HZ.forEachIndexed { note, hz ->
            for (i in 0 until noteSamples) {
                val envelope = minOf(1.0, i / fadeSamples, (noteSamples - 1 - i) / fadeSamples)
                val sample = (sin(2 * PI * hz * i / SAMPLE_RATE) * AMPLITUDE * envelope * Short.MAX_VALUE).toInt()
                out[index++] = (sample and 0xFF).toByte()
                out[index++] = ((sample shr 8) and 0xFF).toByte()
            }
            if (note < NOTES_HZ.size - 1) index += gapSamples * 2 // silencio entre notas (el array ya está a cero)
        }
        return out
    }
}

/** Reproduce [ToneSynth] por la salida de audio del sistema; si no hay, cae al pitido del sistema. */
class ToneSoundPlayer : SoundPlayer {

    private val data: ByteArray by lazy { ToneSynth.pcm() }

    // En un hilo aparte: abrir la línea de audio puede tardar y esto se llama desde el hilo de la interfaz.
    override fun play() {
        thread(isDaemon = true, name = "irc-sound") {
            try {
                val clip = AudioSystem.getClip()
                try {
                    clip.addLineListener { if (it.type == LineEvent.Type.STOP) clip.close() }
                    clip.open(ToneSynth.format, data, 0, data.size)
                    clip.start()
                } catch (e: Exception) {
                    clip.close()
                    throw e
                }
            } catch (e: Exception) {
                // Sin dispositivo de audio, línea ocupada o entorno sin pantalla
                Log.d(TAG, "No se pudo reproducir el aviso: ${e.message}")
                runCatching { Toolkit.getDefaultToolkit().beep() }
            }
        }
    }

    private companion object {
        const val TAG = "ToneSoundPlayer"
    }
}
