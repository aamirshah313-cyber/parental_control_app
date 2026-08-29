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
val productName = localProperties.getProperty("PRODUCT_NAME", "Guardian Link")
val brandPrimary = localProperties.getProperty("BRAND_PRIMARY_COLOR", "#1366D6")
val appId = localProperties.getProperty("APPLICATION_ID", "com.guardianlink")

android {
    namespace = "com.guardianlink"
    // API 34 is already installed in the local Android SDK.
    compileSdk = 34

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "0.5.4"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        resValue("string", "app_name", productName)
        resValue("color", "brand_primary", brandPrimary)
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
