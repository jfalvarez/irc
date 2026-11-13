package com.jfaf.irc.domain.config

/** Configuración de compilación que necesita la lógica común; cada plataforma da su implementación. */
interface AppConfig {
    /** Palabras (en minúsculas) que, si aparecen en la URL de una imagen, impiden previsualizarla. */
    val imageFilterKeywords: Set<String>

    /** Client ID de OAuth para el login con Google. En escritorio (sin login, decisión 0.1) será "". */
    val googleSignInClientId: String
}

/** "a, B ,,c" -> {"a", "b", "c"} */
fun parseKeywordList(raw: String): Set<String> =
    raw.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
