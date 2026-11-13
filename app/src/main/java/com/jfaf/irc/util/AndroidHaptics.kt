package com.jfaf.irc.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.jfaf.irc.domain.platform.Haptics
import com.jfaf.irc.log.Log

/** Vibración de 150 ms (la lógica que estaba en `MainActivity.vibrate`). */
class AndroidHaptics(private val context: Context) : Haptics {
    override fun vibrate() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.e(TAG, "Error al activar vibración", e)
        }
    }

    private companion object {
        const val TAG = "AndroidHaptics"
        const val VIBRATION_DURATION_MS = 150L
    }
}
