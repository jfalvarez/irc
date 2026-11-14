package com.jfaf.irc.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.jfaf.irc.domain.platform.NetworkMonitor
import com.jfaf.irc.log.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Red con salida a Internet según Android: la red por defecto y su capacidad `INTERNET`. Escucha los
 * cambios durante toda la vida del proceso (es un singleton). Necesita `ACCESS_NETWORK_STATE`.
 */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                update(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }

            override fun onLost(network: Network) = update(false)
        })
    }

    private fun update(online: Boolean) {
        if (_isOnline.value != online) Log.i(TAG, if (online) "Hay red." else "Sin red.")
        _isOnline.value = online
    }

    private fun currentlyOnline(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private companion object {
        const val TAG = "NetworkMonitor"
    }
}
