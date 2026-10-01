plugins { id("com.android.application") }
android {
    namespace = "dash.probe"
    compileSdk = 35
    defaultConfig { applicationId = "dash.probe"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
