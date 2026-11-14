package com.jfaf.irc.domain.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Si el dispositivo tiene red con salida a Internet. Lo usa la sesión IRC para no gastar intentos de
 * reconexión sin red y para cortar al momento si la red se va. Android: `ConnectivityManager`;
 * escritorio: interfaces de red activas.
 */
interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}

/** Sin información de red: siempre se considera que hay (comportamiento anterior). */
object AlwaysOnlineNetworkMonitor : NetworkMonitor {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
}
