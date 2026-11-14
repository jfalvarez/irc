package com.jfaf.irc.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopNetworkMonitorTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() = scope.cancel()

    @Volatile private var online = true

    @Test
    fun sigueLoQueDiceLaConsulta() = runBlocking {
        val monitor = DesktopNetworkMonitor(scope, intervalMs = 10, probe = { online })
        assertTrue(monitor.isOnline.value)
        online = false
        withTimeout(2_000) { monitor.isOnline.first { !it } }
        online = true
        withTimeout(2_000) { monitor.isOnline.first { it } }
        Unit
    }

    @Test
    fun siNoSePuedeConsultar_seDaPorBuena() {
        val monitor = DesktopNetworkMonitor(scope, intervalMs = 10, probe = { throw IllegalStateException("sin permisos") })
        assertTrue(monitor.isOnline.value)
    }
}
