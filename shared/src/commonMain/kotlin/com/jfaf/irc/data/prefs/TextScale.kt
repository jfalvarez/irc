package com.jfaf.irc.data.prefs

/**
 * Tamaño del texto de la app (UI-29): un factor que se multiplica por el tamaño de letra del sistema.
 * Por defecto, normal (1,0).
 */
enum class TextScale(val factor: Float) {
    SMALL(0.85f),
    NORMAL(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f);

    companion object {
        val Default = NORMAL

        /** Lee el valor guardado; lo desconocido o ausente da el predeterminado. */
        fun fromStorage(value: String?): TextScale = entries.firstOrNull { it.name == value } ?: Default
    }
}
