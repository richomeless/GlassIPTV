plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.glass.iptv"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.glass.iptv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    val m = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$m")
    implementation("androidx.media3:media3-exoplayer-hls:$m")
    implementation("androidx.media3:media3-session:$m")
    implementation("androidx.media3:media3-ui:$m")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
