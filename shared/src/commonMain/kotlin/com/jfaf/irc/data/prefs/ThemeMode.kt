package com.jfaf.irc.data.prefs

/** Aspecto elegido por el usuario (UI-2). Por defecto, oscuro. */
enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM;

    /** Si hay que pintar el tema oscuro, dado lo que dice el sistema. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        DARK -> true
        LIGHT -> false
        SYSTEM -> systemDark
    }

    companion object {
        val Default = DARK

        /** Lee el valor guardado; lo desconocido o ausente da el predeterminado. */
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: Default
    }
}
