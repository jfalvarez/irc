package com.jfaf.irc.desktop

/**
 * Lo necesario para iniciar sesión con Google en escritorio. Sale de `local.properties` al compilar
 * ([DesktopBuildConfig]); si falta algo, [fromBuild] da null y la app no ofrece iniciar sesión.
 *
 * - [clientId] y [clientSecret]: cliente OAuth de tipo «Aplicación de escritorio» del proyecto de Firebase.
 *   En apps de escritorio Google no trata el secreto como secreto; lo que protege el flujo es PKCE.
 * - [firebaseApiKey]: «Clave de API web» del proyecto (la de `google-services.json` suele estar limitada a Android).
 * - [firebaseProjectId]: id del proyecto (para Firestore).
 */
data class DesktopGoogleConfig(
    val clientId: String,
    val clientSecret: String,
    val firebaseApiKey: String,
    val firebaseProjectId: String
) {
    companion object {
        fun fromBuild(): DesktopGoogleConfig? = of(
            clientId = DesktopBuildConfig.GOOGLE_CLIENT_ID,
            clientSecret = DesktopBuildConfig.GOOGLE_CLIENT_SECRET,
            firebaseApiKey = DesktopBuildConfig.FIREBASE_API_KEY,
            firebaseProjectId = DesktopBuildConfig.FIREBASE_PROJECT_ID
        )

        fun of(clientId: String, clientSecret: String, firebaseApiKey: String, firebaseProjectId: String): DesktopGoogleConfig? =
            if (listOf(clientId, clientSecret, firebaseApiKey, firebaseProjectId).any { it.isBlank() }) null
            else DesktopGoogleConfig(clientId, clientSecret, firebaseApiKey, firebaseProjectId)
    }
}
