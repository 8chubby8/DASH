import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        versionCode = 35
        versionName = "1.5.15"

        // Stamped so About DASH can say when this build was made — a sideloaded head unit has no
        // store listing to read a date from, and "which build is on the tablet" is the first
        // question any bug report has to answer.
        buildConfigField(
            "String",
            "BUILD_DATE",
            "\"${LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK))}\"",
        )
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
        // For BUILD_DATE above, read by System › About DASH.
        buildConfig = true
    }
}

/**
 * Ship the GPL-3.0 text with the app (roadmap 1.5.14).
 *
 * GPL-3.0 §5(d) requires an interactive program to display Appropriate Legal Notices, including how
 * to view a copy of the licence — so System › Licence reads the text at runtime rather than carrying
 * a paraphrase. Copying it from the repo root at build time means the licence DASH shows and the
 * licence the repo carries can never drift apart: there is one file, and it is the one in git.
 *
 * It copies into the source assets rather than a generated directory on purpose. A generated asset
 * folder has to be threaded into AGP's asset-merge task ordering to be picked up reliably; copying
 * into `src/main/assets` is a line of build script that always works, and leaves the file present
 * and committed so an IDE build that never runs the task still has it.
 */
val copyLicence by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE"))
    into(layout.projectDirectory.dir("src/main/assets"))
}
tasks.named("preBuild") { dependsOn(copyLicence) }

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
