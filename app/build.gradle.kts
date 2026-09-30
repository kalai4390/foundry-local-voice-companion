import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val sdkArchive = providers.gradleProperty("foundryAar").orNull?.let { file(it) }
    ?: rootProject.file("../feasibility/runtime/android-artifacts/foundry-local-ipc-sdk-0.1.6.aar")
check(sdkArchive.isFile) { "Acquire the official Foundry Local IPC 0.1.6 AAR; see README or pass -PfoundryAar=..." }
val sdkHash = MessageDigest.getInstance("SHA-256").digest(sdkArchive.readBytes())
    .joinToString("") { byteValue -> "%02x".format(byteValue) }
check(sdkHash == "7dc74260d2dbd16c2c3c02bcd9de1d852bb89366f71a63ea9813fe764c2665c1") {
    "Foundry Local IPC AAR checksum mismatch."
}

android {
    namespace = "dev.daylight.local"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.daylight.local"
        minSdk = 33
        targetSdk = 36
        versionCode = 9
        versionName = "0.3.4-preview"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true }
}

dependencies {
    implementation(project(":core"))
    implementation(files(sdkArchive))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
