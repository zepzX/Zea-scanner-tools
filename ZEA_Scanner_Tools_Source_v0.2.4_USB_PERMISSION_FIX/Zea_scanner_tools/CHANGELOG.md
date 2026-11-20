# Changelog

## 0.2.4 — USB permission/default-handler improvement
- Added Android USB attach intent filter for FTDI VID 0x0403.
- Added `singleTop` attach-intent handling so an already-running Activity processes the new USB device intent.
- Uses Android's default USB handler flow to reduce repeated permission interaction when the user selects ZEA as the default handler.
- Kept manual SCAN USB and DISCONNECT controls.
- Android still controls USB permission; repeated prompts cannot be programmatically bypassed on OEM builds that do not honor the default-handler association.

plugins {
    id("com.android.application")
}

android {
    namespace = "com.zea.scannertools"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zea.scannertools"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.2.4"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
