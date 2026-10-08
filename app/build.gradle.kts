plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alteon.appgate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alteon.appgate"
        // Fire OS 6 is API 25; Fire OS 7 is API 28; Fire OS 8 is API 30.
        minSdk = 25
        // 34 avoids Android 15's forced edge-to-edge layout on newer Go tablets.
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sideload-only app: sign release with the debug key until a real keystore exists.
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
    testImplementation("junit:junit:4.13.2")
}
