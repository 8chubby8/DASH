plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.dash.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dash.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 28
        versionName = "1.5.8"
    }

    // A fixed key for debug/nightly builds so every build (local and the CI nightly) shares one
    // signature — testers can update a nightly in place instead of uninstall-reinstall. This is a
    // throwaway debug key with no security value (like Android's public default debug key); it is NOT
    // a release/Play signing key. A real release would use a keystore held as a CI secret instead.
    signingConfigs {
        getByName("debug") {
            storeFile = file("nightly.keystore")
            storePassword = "dashnightly"
            keyAlias = "dash"
            keyPassword = "dashnightly"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.usb.serial)
    debugImplementation(libs.androidx.ui.tooling)
}
