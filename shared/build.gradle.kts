import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    // Compilador de Compose: el modelo del chat (AnnotatedString) se pinta en :app y debe seguir siendo estable.
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.jfaf.irc.shared"
        compileSdk = 37
        minSdk = 29
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // Ejecuta commonTest también en la JVM del host con el classpath de Android.
        withHostTest {}
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kermit)
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.datastore.preferences.core)
            // K9: el modelo de chat usa AnnotatedString/Color de Compose (multiplataforma).
            api(libs.compose.mp.runtime)
            api(libs.compose.mp.ui.text)
            api(libs.compose.mp.ui.graphics)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
