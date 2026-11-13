package com.jfaf.irc.domain.platform

import kotlinx.coroutines.flow.StateFlow

/** Comprueba si la versión instalada es obsoleta. Android: Firebase Remote Config; escritorio: no-op. */
interface UpdateChecker {
    val isUpdateRequired: StateFlow<Boolean>
    fun check()
}

/** Regla común: hay que actualizar si se conoce la versión actual y es menor que la mínima exigida. */
fun isUpdateRequired(currentVersion: Long, minRequiredVersion: Long): Boolean =
    currentVersion != UNKNOWN_VERSION && currentVersion < minRequiredVersion

const val UNKNOWN_VERSION = -1L
