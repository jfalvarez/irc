import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Login de Google en escritorio: claves de local.properties (fuera de git), compiladas en DesktopBuildConfig.
// Si falta alguna, la app no ofrece iniciar sesión (como antes).
val desktopConfigKeys = mapOf(
    "GOOGLE_CLIENT_ID" to "google.desktop.clientId", // cliente OAuth «Aplicación de escritorio» del proyecto de Firebase
    "GOOGLE_CLIENT_SECRET" to "google.desktop.clientSecret",
    "FIREBASE_API_KEY" to "firebase.webApiKey", // «Clave de API web» de la configuración del proyecto de Firebase
    "FIREBASE_PROJECT_ID" to "firebase.projectId"
)
val desktopConfigValues: Map<String, String> = run {
    val props = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.reader(Charsets.UTF_8)?.use { props.load(it) }
    desktopConfigKeys.mapValues { (_, key) -> props.getProperty(key, "").trim() }
}
val generateDesktopBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/desktopBuildConfig/kotlin")
    val values = desktopConfigValues
    inputs.property("values", values)
    outputs.dir(outputDir)
    doLast {
        fun literal(value: String) =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        val file = outputDir.get().file("com/jfaf/irc/desktop/DesktopBuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(buildString {
            appendLine("package com.jfaf.irc.desktop")
            appendLine()
            appendLine("/** Generado por :desktopApp:generateDesktopBuildConfig a partir de local.properties. */")
            appendLine("internal object DesktopBuildConfig {")
            values.forEach { (name, value) -> appendLine("    const val $name = ${literal(value)}") }
            appendLine("}")
        })
    }
}
kotlin.sourceSets.named("main") { kotlin.srcDir(generateDesktopBuildConfig) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs) // Compose Desktop + el runtime nativo de Skia de este sistema
    implementation(libs.kotlinx.coroutines.swing) // Dispatchers.Main (viewModelScope, Compose)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp) // motor HTTP de Ktor (OkHttp ya lo trae Coil)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jna.platform) // DPAPI (Crypt32Util) para cifrar la sesión guardada

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(compose.desktop.uiTestJUnit4) // pruebas de la UI de escritorio (sin ventana)
}

tasks.test {
    useJUnit()
}

val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

compose.desktop {
    application {
        mainClass = "com.jfaf.irc.desktop.MainKt"

        nativeDistributions {
            // Cada formato sólo se puede generar en su propio sistema (MSI en Windows, DEB en Linux, DMG en macOS).
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "IRC"
            packageVersion = "1.0.0"
            description = "Cliente IRC para IRC-Hispano"
            vendor = "jfaf"

            // Módulos del runtime recortado con jlink: lo que sugiere `suggestRuntimeModules` más lo que
            // el análisis no ve: curvas elípticas del TLS (jdk.crypto.ec) y, en Windows, el almacén de
            // certificados del sistema que usa DesktopTls (jdk.crypto.mscapi, sólo existe en los JDK de Windows).
            modules("java.instrument", "jdk.unsupported", "jdk.crypto.ec")
            if (isWindows) modules("jdk.crypto.mscapi")

            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "IRC"
                shortcut = true
                perUserInstall = true // sin permisos de administrador
                // Identifica la app en las actualizaciones del MSI: no cambiar nunca
                upgradeUuid = "0fc9c5b1-9401-4825-aeb8-4ea42085a908"
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }

        buildTypes.release.proguard {
            version.set("7.10.0") // la 7.8.0 por defecto no lee los metadatos de Kotlin 2.4
            isEnabled.set(true)
            // Sin ofuscar: los nombres de clase se conservan en los errores y no se rompe la reflexión de Room y Koin
            obfuscate.set(false)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }
}
