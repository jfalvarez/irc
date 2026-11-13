package com.jfaf.irc.domain.platform

/**
 * Mantiene viva la conexión mientras la app no está visible.
 * Android: servicio en primer plano (con su notificación). Escritorio: nada, vive lo que viva el proceso.
 */
interface ConnectionHost {
    /** Antes de conectar. */
    fun start()
    /** Tras desconectar: retira lo que mantenía viva la conexión. */
    fun stop()
}
