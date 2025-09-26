import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    kotlin("kapt")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics") // Plugin de Crashlytics
}

// Leer propiedades desde local.properties
val localProperties = Properties()
val localPropertiesFile = project.rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

// Asegúrate de que admob.appId en local.properties NO tenga comillas alrededor del valor.
// Ejemplo en local.properties: admob.appId=ca-app-pub-3940256099942544~3347511713
val admobAppIdFromLocalProps = localProperties.getProperty("admob.appId", "ca-app-pub-3940256099942544~3347511713") // Default al ID de prueba
println("ADMOB APP ID FROM GRADLE: '$admobAppIdFromLocalProps'") // Para depuración, puedes quitarlo después

// Obtener las palabras clave del filtro de imagen, o una cadena vacía si no se define
val imageFilterKeywords = localProperties.getProperty("IMAGE_FILTER_KEYWORDS", "")
println("IMAGE FILTER KEYWORDS FROM GRADLE: '$imageFilterKeywords'") // Para depuración

android {
    namespace = "com.jfaf.irc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jfaf.irc"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["admobAppId"] = admobAppIdFromLocalProps

        // Exponer IMAGE_FILTER_KEYWORDS como un campo en BuildConfig
        buildConfigField("String", "IMAGE_FILTER_KEYWORDS", "\"$imageFilterKeywords\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Considera habilitar ProGuard/R8 para release
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {}
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true // Habilitar BuildConfig
    }
    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended) 
    implementation(libs.guava)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    // Firebase - Asegúrate de que el BoM (Bill of Materials) esté presente
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.commonKtx)
    implementation(libs.firebase.analytics)       // Firebase Analytics KTX
    implementation(libs.firebase.remoteconfig)   // Firebase Remote Config KTX
    implementation(libs.firebase.crashlytics)    // Firebase Crashlytics KTX

    // AdMob
    implementation("com.google.android.gms:play-services-ads:23.0.0") // Versión cambiada a 23.0.0

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

kapt {
    correctErrorTypes = true
}
