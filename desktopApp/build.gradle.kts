import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs) // Compose Desktop + el runtime nativo de Skia de este sistema
    implementation(libs.kotlinx.coroutines.swing) // Dispatchers.Main (viewModelScope, Compose)

    testImplementation(libs.kotlin.test)
    testImplementation(compose.desktop.uiTestJUnit4) // pruebas de la UI de escritorio (sin ventana)
}

tasks.test {
    useJUnit()
}

compose.desktop {
    application {
        mainClass = "com.jfaf.irc.desktop.MainKt"
    }
}
