package com.jfaf.irc.domain.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Si la app está a la vista del usuario (decide, p. ej., si se notifica un privado). */
class AppVisibility {
    private val _isInForeground = MutableStateFlow(true) // se asume visible al arrancar
    val isInForeground: StateFlow<Boolean> = _isInForeground.asStateFlow()

    fun enteredForeground() { _isInForeground.value = true }
    fun enteredBackground() { _isInForeground.value = false }
}
