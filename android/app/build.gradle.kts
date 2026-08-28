import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
}

android {
    namespace = "com.lorongtea.mobile"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.lorongtea.mobile"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    val releaseUrl = providers.gradleProperty("lorongTeaApiUrl")
        .getOrElse("https://api.lorongtea.invalid/")
    val releaseUri = URI(releaseUrl)
    require(releaseUri.scheme == "https" && releaseUri.host != null && releaseUrl.endsWith("/")) {
        "lorongTeaApiUrl must be an HTTPS base URL ending in /"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_URL", "\"http://10.0.2.2:8080/\"")
        }
        release {
            buildConfigField("String", "API_URL", "\"$releaseUrl\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
}
