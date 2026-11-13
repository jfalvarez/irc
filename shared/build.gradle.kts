import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    // Compilador de Compose: el modelo del chat (AnnotatedString) se pinta en :app y debe seguir siendo estable.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform) // Compose Resources (textos)
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
        androidResources { enable = true } // Compose Resources en Android
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
            api(libs.compose.mp.resources) // :app usa stringResource(Res.string.…)
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)
            api(libs.koin.core) // commonModule
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // Leer Compose Resources en la JVM de escritorio necesita el runtime nativo de Skia (skiko).
        named("desktopTest").dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

dependencies {
    // Room genera el código de la base de datos para cada target.
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

compose.resources {
    packageOfResClass = "com.jfaf.irc.resources"
    publicResClass = true // :app usa Res.string en sus composables
}

room {
    schemaDirectory("$projectDir/schemas")
}
