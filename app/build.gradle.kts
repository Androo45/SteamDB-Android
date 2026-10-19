plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.steamdb"
    compileSdk = 35

   // En tu app/build.gradle.kts
  defaultConfig {
    applicationId = "com.example.steamdb"
    minSdk = 26
    targetSdk = 34
    versionCode = 12 // Subimos de 11 a 12
    versionName = "1.1.1" // Actualizamos a v1.1.1
}

    // Firma permanente: si existen las variables de entorno KEYSTORE_FILE /
    // KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD, TODAS las APKs (debug y
    // release) se firman con la misma clave, así las actualizaciones nunca
    // dan "conflicto con un paquete". Sin variables, se usa la firma debug.
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_FILE")
            if (ksPath != null && rootProject.file(ksPath).exists()) {
                storeFile = rootProject.file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        val signed = System.getenv("KEYSTORE_FILE")?.let { rootProject.file(it).exists() } == true
        debug {
            if (signed) signingConfig = signingConfigs.getByName("release")
        }
        release {
            if (signed) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}


