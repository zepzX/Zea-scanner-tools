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
