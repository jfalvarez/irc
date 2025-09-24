import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android) // Updated Hilt plugin
    kotlin("kapt") // Necesario para el procesador de anotaciones de Hilt
    id("com.google.gms.google-services")
}

// Leer el ID de AdMob desde local.properties
val localProperties = Properties()
val localPropertiesFile = project.rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val admobAppIdFromLocalProps = localProperties.getProperty("admob.appId", "YOUR_DEFAULT_ADMOB_APP_ID_IF_NOT_FOUND") // Proporciona un valor por defecto si no se encuentra
println("Valor de admobAppId leído de local.properties: '$admobAppIdFromLocalProps'") // IMPRIMIR VALOR


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

        // Usar manifestPlaceholders para el ID de AdMob
        manifestPlaceholders["admobAppId"] = admobAppIdFromLocalProps
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // También puedes definir placeholders específicos por build type si es necesario
            // manifestPlaceholders["admobAppId"] = localProperties.getProperty("admob.releaseAppId", "YOUR_RELEASE_ADMOB_APP_ID")
        }
        debug {
            // manifestPlaceholders["admobAppId"] = localProperties.getProperty("admob.debugAppId", "ca-app-pub-3940256099942544~3347511713") // Ejemplo para debug
        }
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
    implementation(libs.guava)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    implementation(libs.androidx.material3)
    implementation(libs.firebase.config)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.commonKtx) 
    implementation(libs.firebase.analytics)     
    implementation(libs.firebase.remoteconfig)
    implementation("com.google.android.gms:play-services-ads:23.1.0")

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
