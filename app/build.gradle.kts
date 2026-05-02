import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val nothingApiKeyProvider = providers.gradleProperty("TOYPH_NOTHING_API_KEY")
    .orElse(providers.provider { localProperties.getProperty("toyph.nothingApiKey", "test") })

android {
    namespace = "com.toyph.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.toyph.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["nothingApiKey"] = nothingApiKeyProvider.get()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = false
        resValues = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))
    implementation(libs.kotlinx.coroutines.android)
}
