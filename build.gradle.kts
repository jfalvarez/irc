
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    // AGP 9 trae Kotlin integrado (KGP 2.2.10); declarar KGP aquí fija la versión del catálogo.
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    id("com.google.gms.google-services") version "4.6.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false // Añadido plugin de Crashlytics
}
