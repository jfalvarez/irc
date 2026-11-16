package com.jfaf.irc.domain.chat.command

/**
 * Los comandos que escribe el usuario en el campo de mensajes (`/join`…), en el orden en que se sugieren al
 * autocompletar: los más usados primero. Tiene que coincidir con los que entiende [CommandProcessor]
 * (`ChatCommandsTest` lo comprueba).
 */
object ChatCommands {
    val NAMES: List<String> = listOf(
        "join", "msg", "me", "part", "query", "nick", "away", "topic", "list", "clear", "quit", "help"
    )
}
