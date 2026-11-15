package com.jfaf.irc.data.prefs

/** Cómo se pintan los mensajes de conversación (UI-16). */
enum class MessageDensity {
    /** Avatar y nick una vez por tanda de mensajes seguidos del mismo autor. Por defecto. */
    COMFORTABLE,

    /** Una línea por mensaje, `<nick> texto`, como en un cliente IRC clásico. */
    COMPACT;

    companion object {
        val Default = COMFORTABLE

        /** Lee el valor guardado; lo desconocido o ausente da el predeterminado. */
        fun fromStorage(value: String?): MessageDensity = entries.firstOrNull { it.name == value } ?: Default
    }
}
