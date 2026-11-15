package com.jfaf.irc.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/**
 * Llama a [onSwipe] cuando un dedo se desplaza de derecha a izquierda (sobre todo en horizontal) desde cualquier punto.
 * Sólo observa los eventos (pasada inicial) y no los consume: ni quita el desplazamiento de la lista ni estorba al
 * cajón de las pestañas, que recibe los arrastres de izquierda a derecha.
 */
fun Modifier.openOnLeftSwipe(enabled: Boolean, onSwipe: () -> Unit): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        val threshold = viewConfiguration.touchSlop * 6
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dx = 0f
            var dy = 0f
            var fired = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
                if (!fired && dx < -threshold && abs(dx) > 2 * abs(dy)) {
                    fired = true
                    onSwipe()
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
