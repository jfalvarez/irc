package com.jfaf.irc.desktop

import java.io.File

/** Carpeta de datos del usuario: ajustes (DataStore) e historial (Room). */
object DesktopPaths {
    /** `-Dirc.dataDir=...` cambia la carpeta (pruebas y desarrollo sin tocar la del usuario). */
    fun defaultDataDir(): File {
        System.getProperty("irc.dataDir")?.let { return File(it) }
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home")
        val base = when {
            "win" in os -> System.getenv("APPDATA")?.let(::File) ?: File(home, "AppData/Roaming")
            "mac" in os -> File(home, "Library/Application Support")
            else -> System.getenv("XDG_DATA_HOME")?.let(::File) ?: File(home, ".local/share")
        }
        return File(base, "IRC")
    }
}
