package com.jfaf.irc.domain.platform

/** Aviso táctil al llegar un privado o conectarse un amigo con la app visible (en escritorio no hace nada). */
interface Haptics {
    fun vibrate()
}
