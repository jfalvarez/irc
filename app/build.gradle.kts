plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    kotlin("kapt") // Necesario para el procesador de anotaciones de Hilt
}

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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
            // Si encuentras otros duplicados de META-INF, puedes añadirlos aquí también:
            // excludes += "/META-INF/LICENSE"
            // excludes += "/META-INF/LICENSE.txt"
            // excludes += "/META-INF/NOTICE"
            // excludes += "/META-INF/NOTICE.txt"
            // excludes += "/META-INF/DEPENDENCIES"
            // excludes += "/META-INF/LGPL2.1" // Común con Netty
             excludes += "META-INF/io.netty.versions.properties" // También de Netty
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-process:2.6.1") // DEPENDENCIA AÑADIDA AQUÍ
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.guava) // Or a newer version
//    implementation(libs.kitteh.irc.client) // O la última versión estable que encuentres
//    implementation("org.kitteh.irc:client-lib:9.0.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Kotlinx Coroutines
    implementation(libs.kotlinx.coroutines.core) // Changed to use version catalog

    // Hilt & Navigation
    implementation("com.google.dagger:hilt-android:2.57.1")
    kapt("com.google.dagger:hilt-compiler:2.57.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.9.4") // Added Navigation Compose

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Allow references to generated code
kapt {
    correctErrorTypes = true
}