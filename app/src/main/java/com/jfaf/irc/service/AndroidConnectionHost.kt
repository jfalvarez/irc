package com.jfaf.irc.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.jfaf.irc.domain.platform.ConnectionHost

/** [ConnectionHost] de Android: el servicio en primer plano `IrcService`. */
class AndroidConnectionHost(
    private val context: Context
) : ConnectionHost {
    override fun start() = send(IrcService.ACTION_START)
    override fun stop() = send(IrcService.ACTION_STOP)

    /** `IrcService.onStartCommand` llama siempre a `startForeground` en los 5 s siguientes. */
    private fun send(action: String) {
        ContextCompat.startForegroundService(context, Intent(context, IrcService::class.java).setAction(action))
    }
}
