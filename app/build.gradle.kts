import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The version DASH reports is read from changelog.md, not written here (roadmap 1.6.11).
 *
 * **Because it was written here, and it went stale.** `versionName` sat at 1.6.5 for four versions
 * and a fortnight, wrong in About DASH, wrong in the copyable `DeviceReport`, and wrong in the
 * title of every public nightly release. It had been bumped faithfully for twenty consecutive
 * versions and then broke at 1.6.6, in a commit that edited this very file to add [copySvgSubset]
 * below — which is the whole lesson: it was not forgotten by someone who never looked at the file,
 * it was missed by someone looking straight at it.
 *
 * Same discipline and same reasoning as [copyLicence] and [copySvgSubset]: **one file, in git, read
 * by everything that needs it.** A version number kept in two places diverges silently, and it
 * diverged in exactly the direction those two tasks exist to prevent — the copy nobody edits going
 * quietly wrong while the copy everybody edits stays right. changelog.md is already the source of
 * truth by project rule (*"a version number is not complete until a changelog entry exists for
 * it"*), so it is now the source of truth in fact.
 *
 * **The digits in the pattern are load-bearing.** changelog.md documents its own entry format with
 * a literal `## Version X.x.x` template inside a fenced block, twenty-six lines above the first
 * real entry — so a pattern matching the words alone finds the template and ships a build named
 * "X.x.x". Requiring `\d+\.\d+\.\d+` is what walks past it.
 *
 * Read through [providers] rather than with `File.readText()` so Gradle tracks changelog.md as a
 * build input and a configuration-cache run re-reads it when it changes. Missing heading is a hard
 * failure, deliberately: a fallback would restore the silent-wrong-number behaviour this exists to
 * end, and a build that stops is a problem that gets fixed.
 */
val dashVersionName: String = run {
    val changelog = rootProject.layout.projectDirectory.file("changelog.md")
    val text = providers.fileContents(changelog).asText.orNull
        ?: error("changelog.md is unreadable — versionName is derived from it (roadmap 1.6.11).")
    Regex("""^## Version (\d+\.\d+\.\d+)\s*$""", RegexOption.MULTILINE)
        .find(text)?.groupValues?.get(1)
        ?: error(
            "No '## Version <n.n.n>' heading found in changelog.md — versionName is derived from " +
                "the topmost one (roadmap 1.6.11). Add the entry before building.",
        )
}

android {
    namespace = "com.dash.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dash.android"
        minSdk = 24
        targetSdk = 35
        versionName = dashVersionName

        // The one number with no source to derive it from, so it stays by hand. It is not the
        // version — it is Android's install-ordering counter, one per *built* version (1.6.1 was
        // documentation only and never got one), and nothing in the changelog records that. It is
        // also far less dangerous stale: a wrong code blocks an in-place nightly update and is
        // noticed immediately, where a wrong name says the wrong thing quietly and forever.
        versionCode = 44

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

/**
 * Ship the SVG subset with the app (roadmap 1.6.6).
 *
 * **This is the previewer's Prime Directive as a line of build script.** `svg-subset.json` at the
 * repository root is the authoritative definition of the SVG DASH renders, read by the parser here
 * and by `panel-preview` — never two hand-maintained lists, because a subset kept in two places
 * diverges silently and in the direction that hurts most: a previewer showing something the app
 * cannot draw. One file, in git, copied to both consumers.
 *
 * Same discipline and same reasoning as [copyLicence] above, including copying into the source
 * assets rather than a generated directory.
 */
val copySvgSubset by tasks.registering(Copy::class) {
    from(rootProject.file("svg-subset.json"))
    into(layout.projectDirectory.dir("src/main/assets"))
}
tasks.named("preBuild") { dependsOn(copySvgSubset) }

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
