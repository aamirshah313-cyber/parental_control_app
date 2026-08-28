import java.util.Properties

plugins {
    id("com.android.application")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { input -> load(input) }
}
val supabaseUrl = localProperties.getProperty("SUPABASE_URL", "")
val supabaseAnonKey = localProperties.getProperty("SUPABASE_ANON_KEY", "")

android {
    namespace = "com.guardianlink"
    // API 34 is already installed in the local Android SDK.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.guardianlink"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.3.1"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
