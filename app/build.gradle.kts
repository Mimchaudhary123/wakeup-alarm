plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wakeup.alarm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wakeup.alarm"
        minSdk = 26
        targetSdk = 33
        versionCode = 2
        versionName = "1.0"
    }

    // Fixed signing key so every new build can be installed OVER the previous one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // No extra libraries needed (the QR scanner was removed).
}
