package com.jfaf.irc.domain.config

/** Configuración de compilación que necesita la lógica común; cada plataforma da su implementación. */
interface AppConfig {
    /** Palabras (en minúsculas) que, si aparecen en la URL de una imagen, impiden previsualizarla. */
    val imageFilterKeywords: Set<String>
}

/** "a, B ,,c" -> {"a", "b", "c"} */
fun parseKeywordList(raw: String): Set<String> =
    raw.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
