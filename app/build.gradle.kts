plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Wird beim automatischen Bau auf GitHub hochgezählt, damit Updates installiert werden können.
val buildNumber = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "io.github.se7enpixel.zeiterfassung"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.se7enpixel.zeiterfassung"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Fester Schlüssel, damit jede neue Version über die alte installiert werden kann,
    // ohne dass die gespeicherten Zeiten verloren gehen.
    signingConfigs {
        create("release") {
            storeFile = file("zeiterfassung.keystore")
            storePassword = "zeiterfassung"
            keyAlias = "zeiterfassung"
            keyPassword = "zeiterfassung"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
