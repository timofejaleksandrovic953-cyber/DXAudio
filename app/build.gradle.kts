plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dxaudio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dxaudio"
        minSdk = 23
        targetSdk = 36
        
        // Версии подтягиваются из GitHub Actions (VERSION_NAME и VERSION_CODE)
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
    }

    // --- НАСТРОЙКА ПОДПИСИ РЕЛИЗА ---
    signingConfigs {
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE")
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            
            // Если собираем на GitHub с ключом — подписываем релизным.
            // Если локально (без ключа) — подписываем дебаг-ключом, чтобы сборка не упала.
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

dependencies {
    val media3Version = "1.11.1"

    // AndroidX Media3 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui-compose-material3:$media3Version")

    // Расширенные кодеки через NextLib (GPL-3.0)
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.11.1-0.16.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.core:core-ktx:1.12.0")
    
    // Иконки для Compose (нужны для стрелочек, паузы и т.д.)
    implementation("androidx.compose.material:material-icons-extended")

    // DocumentFile для SAF (выбор папок)
    implementation("androidx.documentfile:documentfile:1.0.1")
}
