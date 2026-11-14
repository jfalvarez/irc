package com.jfaf.irc.desktop

import com.jfaf.irc.domain.platform.NetworkMonitor
import com.jfaf.irc.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.NetworkInterface

/**
 * Red en el escritorio: hay red si alguna interfaz (que no sea la de bucle local) está activa y tiene
 * dirección. Java no avisa de los cambios, así que se consulta cada [intervalMs]. Es prudente: si no
 * se puede consultar se da por buena, y los adaptadores virtuales (VPN, Hyper-V…) cuentan como red,
 * así que en el peor caso se comporta como antes (sin esperar a la red).
 */
class DesktopNetworkMonitor(
    scope: CoroutineScope,
    private val intervalMs: Long = 5_000,
    private val probe: () -> Boolean = ::anyInterfaceUp
) : NetworkMonitor {

    private val _isOnline = MutableStateFlow(safeProbe())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(intervalMs)
                val online = safeProbe()
                if (online != _isOnline.value) Log.i(TAG, if (online) "Hay red." else "Sin red.")
                _isOnline.value = online
            }
        }
    }

    private fun safeProbe(): Boolean = try {
        probe()
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo consultar la red; se da por buena.", e)
        true
    }

    private companion object {
        const val TAG = "NetworkMonitor"
    }
}

private fun anyInterfaceUp(): Boolean =
    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any {
        it.isUp && !it.isLoopback && it.inetAddresses.hasMoreElements()
    }
