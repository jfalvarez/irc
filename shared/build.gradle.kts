import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    // Compilador de Compose: el modelo del chat (AnnotatedString) se pinta en :app y debe seguir siendo estable.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    compilerOptions {
        // expect/actual de clases (ChatDatabaseConstructor de Room) sigue en beta.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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

    // Android y escritorio son JVM: lo que usa java.net/javax.net (JvmSocketTransport) va en jvmCommonMain.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withJvm()
                withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
            }
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
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    // Room genera el código de la base de datos para cada target.
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}
