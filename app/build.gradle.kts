plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.blyator.mpesa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blyator.mpesa"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Unsigned release apk is fine — LSPosed load debuggable apks
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    // Xposed API — compileOnly: provided by the LSPosed framework at runtime, NOT bundled.
    compileOnly("de.robv.android.xposed:api:82")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("androidx.core:core:1.13.1")
}
