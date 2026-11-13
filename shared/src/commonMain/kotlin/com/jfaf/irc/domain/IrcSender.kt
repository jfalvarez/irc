package com.jfaf.irc.domain

/**
 * Lo mínimo que la lógica de comandos necesita para hablar con el servidor IRC.
 * Permite probar los comandos sin Android y sustituir el transporte por plataforma (KMP).
 */
interface IrcSender {
    fun sendMessage(target: String, message: String)
    fun sendRawCommand(command: String, isSilent: Boolean = false)
}
