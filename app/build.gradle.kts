plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val hasKeystore = System.getenv("KEYSTORE_FILE") != null

android {
    namespace = "com.mkmehmood.everydaykeys"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mkmehmood.everydaykeys"
        minSdk = 24
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }
    signingConfigs {
        if (hasKeystore) create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE")!!)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Falls back to the debug key so every CI build is an installable APK.
            signingConfig = signingConfigs.getByName(if (hasKeystore) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
