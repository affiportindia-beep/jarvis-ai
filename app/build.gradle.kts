plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.plugin.compose)
}

android {
    namespace = "com.jarvisx.app"
    compileSdk = 35
    // Same NDK as the native module, so packaging can strip debug symbols from the .so files.
    ndkVersion = "29.0.13113456"

    defaultConfig {
        applicationId = "com.jarvisx.app"
        minSdk = 30
        targetSdk = 35
        // Each CI build gets a higher versionCode so it installs as an update.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.2.$build"
        ndk {
            // The native engine is built for 64-bit ARM only (every phone from roughly 2017 on).
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Stable signature: CI decodes the private key from the repository secrets
    // JARVISX_KEYSTORE_B64 / JARVISX_KEYSTORE_PASSWORD into JARVISX_KEYSTORE_PATH. With the
    // same key every build installs as an update and keeps downloaded models. Without the
    // secrets (forks, local builds) the standard per-machine debug key is used.
    val stableKeystore = System.getenv("JARVISX_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.isFile }
    signingConfigs {
        if (stableKeystore != null) {
            create("stable") {
                storeFile = stableKeystore
                storePassword = System.getenv("JARVISX_KEYSTORE_PASSWORD")?.trim()
                keyAlias = "jarvisx"
                keyPassword = System.getenv("JARVISX_KEYSTORE_PASSWORD")?.trim()
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (stableKeystore != null) signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (stableKeystore != null) signingConfigs.getByName("stable") else signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // llama.cpp loads its CPU backend variants with dlopen() from nativeLibraryDir,
            // so the .so files must be extracted on install.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":third_party:llama-android"))
    implementation(project(":third_party:microwakeword"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
}
